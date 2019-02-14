/* ==================================================================
 * DefaultSolarSshdService.java - Jun 11, 2017 3:42:49 PM
 * 
 * Copyright 2017 SolarNetwork.net Dev Team
 * 
 * This program is free software; you can redistribute it and/or 
 * modify it under the terms of the GNU General Public License as 
 * published by the Free Software Foundation; either version 2 of 
 * the License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful, 
 * but WITHOUT ANY WARRANTY; without even the implied warranty of 
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU 
 * General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License 
 * along with this program; if not, write to the Free Software 
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 
 * 02111-1307 USA
 * ==================================================================
 */

package net.solarnetwork.solarssh.sshd;

import static net.solarnetwork.util.JsonUtils.getJSONString;

import java.io.IOException;
import java.io.InputStream;
import java.security.KeyPair;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.sshd.common.channel.Channel;
import org.apache.sshd.common.channel.ChannelListener;
import org.apache.sshd.common.io.IoSession;
import org.apache.sshd.common.keyprovider.MappedKeyPairProvider;
import org.apache.sshd.common.session.Session;
import org.apache.sshd.common.session.SessionListener;
import org.apache.sshd.common.session.helpers.AbstractSession;
import org.apache.sshd.common.util.security.SecurityUtils;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.auth.pubkey.CachingPublicKeyAuthenticator;
import org.apache.sshd.server.session.ServerSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;

import net.solarnetwork.solarssh.dao.SshSessionDao;
import net.solarnetwork.solarssh.domain.SshSession;
import net.solarnetwork.solarssh.service.SolarSshService;
import net.solarnetwork.solarssh.service.SolarSshdService;

/**
 * Service to manage the SSH server.
 * 
 * @author matt
 * @version 1.0
 */
public class DefaultSolarSshdService implements SolarSshdService, SessionListener, ChannelListener {

  /** The default port to listen on. */
  public static final int DEFAULT_LISTEN_PORT = 8022;

  private final SshSessionDao sessionDao;

  private int port = DEFAULT_LISTEN_PORT;
  private Resource serverKeyResource;
  private String serverKeyPassword;

  private SshServer server;

  /**
   * Constructor.
   * 
   * @param sessionDao
   *        the session DAO to use
   */
  public DefaultSolarSshdService(SshSessionDao sessionDao) {
    super();
    this.sessionDao = sessionDao;
  }

  private static final Logger LOG = LoggerFactory.getLogger(DefaultSolarSshdService.class);

  /**
   * Start the server.
   */
  public synchronized void start() {
    SshServer s = server;
    if (s != null) {
      return;
    }
    s = SshServer.setUpDefaultServer();
    s.setPort(port);

    KeyPair kp;
    try (InputStream inputStream = serverKeyResource.getInputStream()) {
      kp = SecurityUtils.loadKeyPairIdentity(serverKeyResource.getFilename(), inputStream,
          k -> serverKeyPassword);
    } catch (Exception e) {
      LOG.error("Failed to load server host key resource {}: {}", serverKeyResource,
          e.getMessage());
      if (e instanceof RuntimeException) {
        throw (RuntimeException) e;
      }
      throw new RuntimeException(e);
    }

    LOG.info("Loaded SSH server key from {}", serverKeyResource);
    s.setKeyPairProvider(new MappedKeyPairProvider(Collections.singletonList(kp)));

    // TODO: verify if CachingPublicKeyAuthenticator is appropriate
    s.setPublickeyAuthenticator(
        new CachingPublicKeyAuthenticator(new SolarSshPublicKeyAuthenticator(sessionDao)));

    s.setTcpipForwardingFilter(new SshSessionForwardFilter(sessionDao));

    s.addSessionListener(this);
    s.addChannelListener(this);

    try {
      s.start();
    } catch (IOException e) {
      throw new RuntimeException("Communication error starting SSH server", e);
    }
    LOG.info("SSH server listening on port {}", port);
    server = s;
  }

  /**
   * Stop the server.
   */
  public synchronized void stop() {
    SshServer s = server;
    if (s != null && s.isOpen()) {
      try {
        s.removeSessionListener(this);
        s.removeChannelListener(this);
        s.stop();
      } catch (IOException e) {
        LOG.warn("Communication error stopping SSH server: {}", e.getMessage());
      }
    }
  }

  @Override
  public synchronized ServerSession serverSessionForSessionId(String sessionId) {
    if (server == null || !server.isOpen()) {
      return null;
    }
    List<AbstractSession> sessions = server.getActiveSessions();
    LOG.debug("{} active sessions: {}", sessions != null ? sessions.size() : 0, sessions);
    AbstractSession session = sessions.stream().filter(s -> sessionId.equals(s.getUsername()))
        .findFirst().orElse(null);
    return (ServerSession) session;
  }

  @Override
  public void sessionEvent(Session session, Event event) {
    if (event == SessionListener.Event.Authenticated) {
      String sessionId = session.getUsername();
      SshSession sess = sessionDao.findOne(sessionId);
      if (sess != null) {
        sess.setEstablished(true);
        sess.setServerSession(session);

        Map<String, Object> auditProps = sess.auditEventMap("NODE-CONNECT");
        auditProps.put("date", System.currentTimeMillis());
        IoSession ioSession = session.getIoSession();
        if (ioSession != null) {
          auditProps.put("remoteAddress", ioSession.getRemoteAddress());
        }
        auditProps.put("rport", sess.getReverseSshPort());
        SolarSshService.AUDIT_LOG.info(getJSONString(auditProps, "{}"));
      }
    }
  }

  @Override
  public void sessionException(Session session, Throwable t) {
    LOG.warn("Session {} exception", session.getUsername(), t);
    logSessionClosed(session, t);
  }

  @Override
  public void sessionClosed(Session session) {
    String sessionId = session.getUsername();
    if (sessionId != null) {
      logSessionClosed(session, null);
      SshSession sess = sessionDao.findOne(sessionId);
      if (sess != null) {
        sessionDao.delete(sess);
      }
    }
  }

  private Map<String, Object> auditEventMap(Session session, String eventName) {
    String sessionId = session.getUsername();
    SshSession sess = sessionDao.findOne(sessionId);
    Map<String, Object> map;
    if (sess != null) {
      map = sess.auditEventMap(eventName);
    } else {
      map = new LinkedHashMap<>(8);
      map.put("sessionId", sessionId);
      map.put("event", eventName);
    }
    long now = System.currentTimeMillis();
    map.put("date", now);
    if (sess != null) {
      long secs = (long) Math.ceil((now - sess.getCreated()) / 1000.0);
      map.put("duration", secs);
    }
    return map;

  }

  private void logSessionClosed(Session session, Throwable t) {
    String sessionId = session.getUsername();
    LOG.info("Session {} closed", sessionId);
    Map<String, Object> auditProps = auditEventMap(session, "NODE-DISCONNECT");
    if (t != null) {
      auditProps.put("error", t.toString());
    }
    SolarSshService.AUDIT_LOG.info(getJSONString(auditProps, "{}"));
  }

  @Override
  public void channelOpenSuccess(Channel channel) {
    LOG.debug("Channel {} open success", channel);
  }

  @Override
  public void channelOpenFailure(Channel channel, Throwable reason) {
    LOG.debug("Channel {} open failure", channel, reason);
  }

  @Override
  public void channelClosed(Channel channel, Throwable reason) {
    LOG.debug("Channel {} from session {} closed", channel, channel.getSession().getUsername(),
        reason);
  }

  /**
   * Set the port to listen for SSH connections on.
   * 
   * @param port
   *        the port to listen on
   */
  public void setPort(int port) {
    this.port = port;
  }

  /**
   * Set the resource that holds the server key to use.
   * 
   * @param serverKeyResource
   *        the server key resource
   */
  public void setServerKeyResource(Resource serverKeyResource) {
    this.serverKeyResource = serverKeyResource;
  }

  /**
   * Set the password for the server key resource.
   * 
   * @param serverKeyPassword
   *        the server key password, or {@literal null} for no password
   */
  public void setServerKeyPassword(String serverKeyPassword) {
    this.serverKeyPassword = serverKeyPassword;
  }

}
