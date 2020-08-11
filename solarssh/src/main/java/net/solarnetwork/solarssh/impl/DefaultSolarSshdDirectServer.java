/* ==================================================================
 * DefaultSolarSshdDirectServer.java - 11/08/2020 6:59:41 AM
 * 
 * Copyright 2020 SolarNetwork SolarNetwork.net Dev Team
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

package net.solarnetwork.solarssh.impl;

import static java.util.Arrays.asList;
import static java.util.Collections.unmodifiableList;
import static net.solarnetwork.solarssh.Globals.AUDIT_LOG;
import static net.solarnetwork.solarssh.Globals.DEFAULT_SN_HOST;
import static net.solarnetwork.util.JsonUtils.getJSONString;

import java.io.IOException;
import java.net.SocketAddress;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.sshd.common.FactoryManager;
import org.apache.sshd.common.channel.Channel;
import org.apache.sshd.common.channel.ChannelListener;
import org.apache.sshd.common.config.keys.FilePasswordProvider;
import org.apache.sshd.common.io.IoSession;
import org.apache.sshd.common.keyprovider.FileKeyPairProvider;
import org.apache.sshd.common.session.Session;
import org.apache.sshd.common.session.SessionListener;
import org.apache.sshd.common.session.helpers.AbstractSession;
import org.apache.sshd.server.SshServer;
import org.apache.sshd.server.channel.ChannelSessionFactory;
import org.apache.sshd.server.session.ServerSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;

import net.solarnetwork.solarssh.dao.ActorDao;
import net.solarnetwork.solarssh.domain.SshSession;
import net.solarnetwork.solarssh.service.SolarSshService;

/**
 * Default SSH server service.
 * 
 * @author matt
 * @version 1.0
 */
public class DefaultSolarSshdDirectServer implements SessionListener, ChannelListener {

  /** The default port to listen on. */
  public static final int DEFAULT_LISTEN_PORT = 9022;

  /**
   * The default value for the {@code authTimeoutSecs} property.
   */
  public static final int DEFAULT_AUTH_TIMEOUT_SECS = 300;

  private static final Logger LOG = LoggerFactory.getLogger(DefaultSolarSshdDirectServer.class);

  private final SolarSshService solarSshService;
  private final ActorDao actorDao;

  private String snHost = DEFAULT_SN_HOST;
  private int port = DEFAULT_LISTEN_PORT;
  private int authTimeoutSecs = DEFAULT_AUTH_TIMEOUT_SECS;
  private Resource serverKeyResource;
  private String serverKeyPassword;

  private SshServer server;

  /**
   * Constructor.
   * 
   * @param solarSshService
   *        the SolarSshService to use
   * @param actorDao
   *        the actor DAO to use
   */
  public DefaultSolarSshdDirectServer(SolarSshService solarSshService, ActorDao actorDao) {
    super();
    this.solarSshService = solarSshService;
    this.actorDao = actorDao;
  }

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

    s.setChannelFactories(unmodifiableList(
        asList(ChannelSessionFactory.INSTANCE, new DynamicDirectTcpipFactory(solarSshService))));

    try {
      FileKeyPairProvider keyPairProvider = new FileKeyPairProvider(
          serverKeyResource.getFile().toPath());
      keyPairProvider.setPasswordFinder(FilePasswordProvider.of(serverKeyPassword));
      s.setKeyPairProvider(keyPairProvider);
      LOG.info("Using SSH server key from {}", serverKeyResource);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }

    SolarSshPasswordAuthenticator pwAuth = new SolarSshPasswordAuthenticator(solarSshService,
        actorDao);
    pwAuth.setSnHost(snHost);
    s.setPasswordAuthenticator(pwAuth);

    s.setForwardingFilter(new SshSessionForwardFilter(solarSshService));

    s.addSessionListener(this);
    s.addChannelListener(this);

    s.getProperties().put(FactoryManager.AUTH_TIMEOUT, authTimeoutSecs * 1000L);

    try {
      s.start();
    } catch (IOException e) {
      throw new RuntimeException("Communication error starting SSH server on port " + port, e);
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

  private synchronized ServerSession serverSessionForSessionId(String sessionId) {
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
      SshSession sess = solarSshService.findOne(sessionId);
      if (sess != null) {
        sess.setEstablished(true);
        sess.setServerSession(session);

        Map<String, Object> auditProps = sess.auditEventMap("DIRECT-CONNECT");
        auditProps.put("date", System.currentTimeMillis());
        IoSession ioSession = session.getIoSession();
        if (ioSession != null) {
          auditProps.put("remoteAddress", ioSession.getRemoteAddress());
        }
        auditProps.put("rport", sess.getReverseSshPort());
        AUDIT_LOG.info(getJSONString(auditProps, "{}"));
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
      SshSession sess = solarSshService.findOne(sessionId);
      if (sess != null) {
        // check if matching remote address
        SocketAddress closedSessionRemoteAddress = null;
        SocketAddress daoSessionRemoteAddress = null;
        IoSession ioSession = session.getIoSession();
        if (ioSession != null) {
          closedSessionRemoteAddress = ioSession.getRemoteAddress();
        }
        Session daoServerSession = sess.getServerSession();
        if (daoServerSession != null) {
          IoSession daoIoSession = daoServerSession.getIoSession();
          if (daoIoSession != null) {
            daoSessionRemoteAddress = daoIoSession.getRemoteAddress();
          }
        }
        if (closedSessionRemoteAddress == null || daoSessionRemoteAddress == null
            || closedSessionRemoteAddress.equals(daoSessionRemoteAddress)) {
          solarSshService.delete(sess);
        }
      }
    }
  }

  private Map<String, Object> auditEventMap(Session session, String eventName) {
    String sessionId = session.getUsername();
    SshSession sess = solarSshService.findOne(sessionId);
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
    Map<String, Object> auditProps = auditEventMap(session, "DIRECT-DISCONNECT");
    IoSession ioSession = session.getIoSession();
    if (ioSession != null) {
      auditProps.put("remoteAddress", ioSession.getRemoteAddress());
    }
    if (t != null) {
      auditProps.put("error", t.toString());
    }
    AUDIT_LOG.info(getJSONString(auditProps, "{}"));
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

  /**
   * Set the SolarNetwork host to use.
   * 
   * @param snHost
   *        the host
   * @throws IllegalArgumentException
   *         if {@code snHost} is {@literal null}
   */
  public void setSnHost(String snHost) {
    if (snHost == null) {
      throw new IllegalArgumentException("snHost must not be null");
    }
    this.snHost = snHost;
  }

  /**
   * Set the authorization timeout value, in seconds.
   * 
   * <p>
   * This must be large enough to allow for SolarNode devices to handle the
   * {@literal StartRemoteSsh} instruction.
   * </p>
   * 
   * @param authTimeoutSecs
   *        the timeout seconds
   */
  public void setAuthTimeoutSecs(int authTimeoutSecs) {
    this.authTimeoutSecs = authTimeoutSecs;
  }

}
