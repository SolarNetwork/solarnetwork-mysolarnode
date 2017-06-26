/* ==================================================================
 * DefaultSolarSshService.java - 16/06/2017 7:36:37 PM
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

package net.solarnetwork.solarssh.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.SocketException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.channel.ChannelShell;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.future.CloseFuture;
import org.apache.sshd.common.future.SshFutureListener;
import org.apache.sshd.common.keyprovider.KeyPairProvider;
import org.apache.sshd.common.keyprovider.MappedKeyPairProvider;
import org.apache.sshd.common.util.io.NoCloseInputStream;
import org.apache.sshd.common.util.io.NoCloseOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.solarnetwork.domain.GeneralDatumMetadata;
import net.solarnetwork.solarssh.AuthorizationException;
import net.solarnetwork.solarssh.dao.SshSessionDao;
import net.solarnetwork.solarssh.domain.SolarNetInstruction;
import net.solarnetwork.solarssh.domain.SshCredentials;
import net.solarnetwork.solarssh.domain.SshSession;
import net.solarnetwork.solarssh.domain.SshTerminalSettings;
import net.solarnetwork.util.JsonUtils;

/**
 * Default implementation of {@link SolarSshService}.
 * 
 * @author matt
 * @version 1.0
 */
public class DefaultSolarSshService implements SolarSshService, SshSessionDao {

  private static final Logger AUDIT_LOG = LoggerFactory.getLogger("SshSession.AUDIT");
  private static final Logger log = LoggerFactory.getLogger(DefaultSolarSshService.class);

  private static final String REVERSE_PORT_PARAM = "rport";
  private static final String PORT_PARAM = "port";
  private static final String USER_PARAM = "user";
  private static final String HOST_PARAM = "host";

  private String host = "ssh.solarnetwork.net";
  private int port = 8022;
  private int minPort = 50000;
  private int maxPort = 65000;
  private int sessionExpireSeconds = 300;

  private final SolarNetClient solarNetClient;
  private final ConcurrentMap<Integer, SshSession> portSessionMap = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, SshSession> sessionMap = new ConcurrentHashMap<>();

  /**
   * Constructor.
   * 
   * @param solarNetClient
   *        the SolarNetwork client to use
   */
  public DefaultSolarSshService(SolarNetClient solarNetClient) {
    super();
    this.solarNetClient = solarNetClient;
  }

  @Override
  public SshSession findOne(String id) {
    if (id == null) {
      throw new IllegalArgumentException("Null value not allowed.");
    }
    return sessionMap.get(id);
  }

  @Override
  public void delete(SshSession sess) {
    if (sess == null) {
      throw new IllegalArgumentException("Null value not allowed.");
    }
    endSession(sess);
    portSessionMap.remove(sess.getReverseSshPort(), sess);
    sessionMap.remove(sess.getId(), sess);
  }

  @Override
  public SshSession createNewSession(Long nodeId, long authorizationDate, String authorization)
      throws IOException {
    // see if instruction for StartRemoteSsh already pending, and if so can return session 
    // with that instruction ID
    List<SolarNetInstruction> instructions = solarNetClient.pendingInstructions(nodeId,
        authorizationDate, authorization);
    SolarNetInstruction pending = instructions.stream()
        .filter(instr -> nodeId.equals(instr.getNodeId())).findAny().orElse(null);
    if (pending != null) {
      SshSession sess = portSessionMap.values().stream()
          .filter(s -> pending.getId().equals(s.getStartInstructionId())).findAny().orElse(null);
      if (sess != null) {
        log.info("Returning existing SshSession {} already in {} state", sess.getId(),
            pending.getState());
        return sess;
      }
    }

    String sessionId = UUID.randomUUID().toString();
    Set<Integer> usedPorts = portSessionMap.keySet();

    for (int rport = minPort; rport < maxPort; rport += 2) {
      if (usedPorts.contains(rport)) {
        continue;
      }
      try (ServerSocket socket = new ServerSocket(rport)) {
        socket.setReuseAddress(true);
        try (ServerSocket httpSocket = new ServerSocket(rport + 1)) {
          httpSocket.setReuseAddress(true);
          SshSession sess = new SshSession(System.currentTimeMillis(), sessionId, nodeId, host,
              port, rport, rport + 1);
          if (portSessionMap.putIfAbsent(rport, sess) == null) {
            sessionMap.put(sessionId, sess);
            log.info("SshSession {} created: node {}, rport {}", sessionId, nodeId, rport);
            Map<String, Object> auditProps = sess.auditEventMap("NEW");
            auditProps.put("date", sess.getCreated());
            AUDIT_LOG.info(JsonUtils.getJSONString(auditProps, "{}"));
            return sess;
          }
        } catch (SocketException e) {
          // ignore this one
        }
      } catch (SocketException e) {
        // ignore this one
      }
    }
    throw new IOException("No available port found.");
  }

  @Override
  public SshSession startSession(String sessionId, long authorizationDate, String authorization)
      throws IOException {
    SshSession sess = sessionMap.get(sessionId);
    if (sess == null) {
      throw new AuthorizationException("Session " + sessionId + " not available");
    }

    Map<String, Object> instructionParams = createRemoteSshInstructionParams(sess);

    Long instructionId = solarNetClient.queueInstruction("StartRemoteSsh", sess.getNodeId(),
        instructionParams, authorizationDate, authorization);

    if (instructionId == null) {
      throw new AuthorizationException(
          "Unable to queue StartRemoteSsh instruction for session " + sessionId);
    }

    sess.setStartInstructionId(instructionId);
    return sess;
  }

  @Override
  public SshSession attachTerminal(String sessionId, long authorizationDate, String authorization,
      SshCredentials nodeCredentials, SshTerminalSettings settings, InputStream in,
      OutputStream out) throws IOException {
    SshSession sess = sessionMap.get(sessionId);
    if (sess == null) {
      throw new AuthorizationException("Session " + sessionId + " not available");
    }
    GeneralDatumMetadata meta = solarNetClient.getNodeMetadata(sess.getNodeId(), authorizationDate,
        authorization);
    log.debug("Got node {} metadata info: {}", sess.getNodeId(), meta.getInfo());

    // TODO: extract node public key? by doing nothing, we have at least verified the 
    //       caller has authorization as a user for this node...

    ClientSession clientSession = createClient(sess, nodeCredentials, settings, in, out);
    sess.setClientSession(clientSession);
    return sess;
  }

  private ClientSession createClient(SshSession sess, SshCredentials credentials,
      SshTerminalSettings settings, InputStream in, OutputStream out) throws IOException {
    SshClient client = SshClient.setUpDefaultClient();

    if (credentials.getKeyPair() != null) {
      client.setKeyPairProvider(new MappedKeyPairProvider(credentials.getKeyPair()));
    } else {
      client.setKeyPairProvider(KeyPairProvider.EMPTY_KEYPAIR_PROVIDER);
    }
    client.start();

    ClientSession session = client
        .connect(credentials.getUsername(), "127.0.0.1", sess.getReverseSshPort())
        .verify(30, TimeUnit.SECONDS).getSession();
    if (credentials.getPassword() != null) {
      session.addPasswordIdentity(credentials.getPassword());
    }

    session.auth().verify(30, TimeUnit.SECONDS);

    ChannelShell channel = session.createShellChannel();

    if (settings != null) {
      if (settings.getType() != null) {
        channel.setPtyType(settings.getType());
      }
      channel.setPtyColumns(settings.getCols());
      channel.setPtyLines(settings.getLines());
      channel.setPtyWidth(settings.getWidth());
      channel.setPtyHeight(settings.getHeight());
      settings.getEnvironment().forEach((k, v) -> channel.setEnv(k, v));
    }

    channel.addCloseFutureListener(new SshFutureListener<CloseFuture>() {

      @Override
      public void operationComplete(CloseFuture future) {
        sess.setClientSession(null);
        try {
          out.close();
        } catch (IOException e) {
          log.debug("Error closing SSH output stream: {}", e.getMessage());
        }
      }
    });
    channel.setIn(new NoCloseInputStream(in));

    OutputStream channelOut = new NoCloseOutputStream(out);
    channel.setOut(channelOut);
    channel.setErr(channelOut);
    channel.open().verify(30, TimeUnit.SECONDS);

    return session;
  }

  @Override
  public SshSession stopSession(String sessionId, long authorizationDate, String authorization)
      throws IOException {
    SshSession sess = sessionMap.get(sessionId);
    if (sess == null) {
      throw new AuthorizationException("Session " + sessionId + " not available");
    }

    Map<String, Object> instructionParams = createRemoteSshInstructionParams(sess);

    Long instructionId = solarNetClient.queueInstruction("StopRemoteSsh", sess.getNodeId(),
        instructionParams, authorizationDate, authorization);

    if (instructionId == null) {
      throw new AuthorizationException(
          "Unable to queue StopRemoteSsh instruction for session " + sessionId);
    }

    sess.setStopInstructionId(instructionId);
    delete(sess);
    return sess;
  }

  private void endSession(SshSession sess) {
    if (sess == null) {
      return;
    }
    ClientSession clientSession = sess.getClientSession();
    if (clientSession != null) {
      clientSession.close(false);
      sess.setClientSession(null);
    }
    log.debug("Ended session {}", sess.getId());
    long now = System.currentTimeMillis();
    long secs = (long) Math.ceil((now - sess.getCreated()) / 1000.0);
    Map<String, Object> auditProps = sess.auditEventMap("END");
    auditProps.put("date", now);
    auditProps.put("duration", secs);
    AUDIT_LOG.info(JsonUtils.getJSONString(auditProps, "{}"));
    sess.setEstablished(false);
  }

  private Map<String, Object> createRemoteSshInstructionParams(SshSession sess) {
    Map<String, Object> instructionParams = new HashMap<>(4);
    addInstructionParam(instructionParams, HOST_PARAM, sess.getSshHost());
    addInstructionParam(instructionParams, USER_PARAM, sess.getId());
    addInstructionParam(instructionParams, PORT_PARAM, sess.getSshPort());
    addInstructionParam(instructionParams, REVERSE_PORT_PARAM, sess.getReverseSshPort());
    return instructionParams;
  }

  /**
   * Call periodically to free expired sessions.
   */
  public void cleanupExpiredSessions() {
    final long expireTime = System.currentTimeMillis()
        - TimeUnit.SECONDS.toMillis(sessionExpireSeconds);
    if (log.isDebugEnabled()) {
      log.debug("Examining {} sessions for expiration", portSessionMap.size());
    }
    for (Iterator<SshSession> itr = portSessionMap.values().iterator(); itr.hasNext();) {
      SshSession sess = itr.next();
      if (!sess.isEstablished() && sess.getCreated() < expireTime) {
        log.info("Expiring unestablished SshSession {}: node {}, rport {}", sess.getId(),
            sess.getNodeId(), sess.getReverseSshPort());
        endSession(sess);
        itr.remove();
        sessionMap.remove(sess.getId(), sess);
      }
    }
  }

  private void addInstructionParam(Map<String, Object> params, String key, Object value) {
    int index = (params.size() / 2);
    params.put("parameters[" + index + "].name", key);
    params.put("parameters[" + index + "].value", value);
  }

  public void setMinPort(int minPort) {
    this.minPort = minPort;
  }

  public void setMaxPort(int maxPort) {
    this.maxPort = maxPort;
  }

  public void setHost(String host) {
    this.host = host;
  }

  public void setPort(int port) {
    this.port = port;
  }

  public void setSessionExpireSeconds(int sessionExpireSeconds) {
    this.sessionExpireSeconds = sessionExpireSeconds;
  }

}
