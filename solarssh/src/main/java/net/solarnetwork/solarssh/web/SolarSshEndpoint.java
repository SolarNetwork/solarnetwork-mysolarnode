/* ==================================================================
 * SolarSshEndpoint.java - Jun 11, 2017 3:19:12 PM
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

package net.solarnetwork.solarssh.web;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.Writer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.websocket.CloseReason;
import javax.websocket.Endpoint;
import javax.websocket.EndpointConfig;
import javax.websocket.MessageHandler;
import javax.websocket.Session;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import net.solarnetwork.solarssh.AuthorizationException;
import net.solarnetwork.solarssh.domain.SshCredentials;
import net.solarnetwork.solarssh.domain.SshSession;
import net.solarnetwork.solarssh.service.SolarSshService;
import net.solarnetwork.util.JsonUtils;

/**
 * Websocket endpoint for SolarSSH connections.
 * 
 * @author matt
 * @version 1.0
 */
public class SolarSshEndpoint extends Endpoint implements MessageHandler.Whole<String> {

  /** The user property for the SSH session ID. */
  public static final String SSH_SESSION_ID_USER_PROP = "session-id";

  private static final Logger LOG = LoggerFactory.getLogger(SolarSshEndpoint.class);

  private final SolarSshService solarSshService;

  private Session websocketSession;
  private SshSession sshSession;

  private Writer wsInputSink;

  @Autowired
  public SolarSshEndpoint(SolarSshService solarSshService) {
    super();
    this.solarSshService = solarSshService;
  }

  @Override
  public void onOpen(Session session, EndpointConfig config) {
    Map<String, List<String>> params = session.getRequestParameterMap();
    List<String> values = params.get("sessionId");
    if (values == null || values.isEmpty()) {
      throw new AuthorizationException("Missing required sessionId parameter");
    }
    String sshSessionId = values.get(0);
    sshSession = solarSshService.findOne(sshSessionId);

    websocketSession = session;
    session.addMessageHandler(this);
  }

  @Override
  public void onClose(Session session, CloseReason closeReason) {
    // TODO Auto-generated method stub
    super.onClose(session, closeReason);
  }

  @Override
  public void onError(Session session, Throwable thr) {
    // TODO Auto-generated method stub
    super.onError(session, thr);
  }

  @Override
  public void onMessage(String msg) {
    if (wsInputSink != null) {
      try {
        wsInputSink.write(msg);
        wsInputSink.flush();
      } catch (IOException e) {
        LOG.error("IOException for node {} session {}", sshSession.getNodeId(), sshSession.getId(),
            e);
      }
      return;
    }
    authenticate(msg);
  }

  private void authenticate(String msg) {
    Map<String, ?> msgData = JsonUtils.getStringMap(msg);
    if (msgData == null) {
      throw new AuthorizationException("Message not provided");
    }
    Object cmd = msgData.get("cmd");
    if (!"attach-ssh".equals(cmd)) {
      throw new AuthorizationException("'attach-ssh' message not provided; got " + cmd);
    }
    Object data = msgData.get("data");
    if (!(data instanceof Map)) {
      throw new AuthorizationException("'attach-ssh' data not provided");
    }
    Map<?, ?> dataMap = (Map<?, ?>) data;
    Object auth = dataMap.get("authorization");
    Object authDate = dataMap.get("authorization-date");
    if (!(auth instanceof String && authDate instanceof Number)) {
      throw new AuthorizationException(
          "'attach-ssh' authorization or authorization-date data not provided");
    }

    Object uname = dataMap.get("username");
    Object pass = dataMap.get("password");
    // TODO: keypair dataMap.get("keypair");

    SshCredentials creds;
    if (uname != null) {
      creds = new SshCredentials(uname.toString(), (pass != null ? pass.toString() : null));
    } else {
      throw new AuthorizationException("'attach-ssh' username data not provided");
    }

    try {
      PipedInputStream sshStdin = new PipedInputStream();
      PipedOutputStream pipeOut = new PipedOutputStream(sshStdin);

      wsInputSink = new OutputStreamWriter(pipeOut, "UTF-8");

      OutputStream sshStdout = new AsyncTextOutputStream(websocketSession.getAsyncRemote());

      SshSession session = solarSshService.registerClient(sshSession.getId(),
          ((Number) authDate).longValue(), auth.toString(), creds, sshStdin, sshStdout);
      sshSession = session;

      Map<String, Object> resultMsg = new LinkedHashMap<>(2);
      resultMsg.put("success", true);
      resultMsg.put("message", "Ready to attach");

      websocketSession.getBasicRemote().sendText(JsonUtils.getJSONString(resultMsg,
          "{\"success\":false,\"message\":\"Error serializing JSON response\"}"));
    } catch (IOException e) {
      LOG.error("IOException for node {} session {}", sshSession.getNodeId(), sshSession.getId(),
          e);
    }

  }

}
