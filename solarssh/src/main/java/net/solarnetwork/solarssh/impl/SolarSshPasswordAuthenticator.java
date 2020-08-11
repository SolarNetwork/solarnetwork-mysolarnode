/* ==================================================================
 * SolarSshPasswordAuthenticator.java - 11/08/2020 11:08:31 AM
 * 
 * Copyright 2020 SolarNetwork.net Dev Team
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

import static java.util.Collections.singletonMap;
import static net.solarnetwork.solarssh.Globals.DEFAULT_SN_HOST;
import static net.solarnetwork.solarssh.service.SolarNetClient.INSTRUCTION_TOPIC_START_REMOTE_SSH;
import static net.solarnetwork.solarssh.service.SolarNetClient.INSTRUCTION_TOPIC_STOP_REMOTE_SSH;

import java.io.IOException;
import java.util.Date;
import java.util.Map;

import org.apache.sshd.server.auth.AsyncAuthException;
import org.apache.sshd.server.auth.password.PasswordAuthenticator;
import org.apache.sshd.server.auth.password.PasswordChangeRequiredException;
import org.apache.sshd.server.session.ServerSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;

import net.solarnetwork.solarssh.dao.ActorDao;
import net.solarnetwork.solarssh.domain.Actor;
import net.solarnetwork.solarssh.domain.DirectSshUsername;
import net.solarnetwork.solarssh.domain.SolarNodeInstructionState;
import net.solarnetwork.solarssh.domain.SshSession;
import net.solarnetwork.solarssh.service.SolarNetClient;
import net.solarnetwork.solarssh.service.SolarSshService;
import net.solarnetwork.web.security.AuthorizationV2Builder;

/**
 * {@link PasswordAuthenticator} for direct SolarSSH connections.
 * 
 * @author matt
 * @version 1.0
 */
public class SolarSshPasswordAuthenticator implements PasswordAuthenticator {

  /**
   * The default value for the {@code maxNodeInstructionWaitSecs} property.
   */
  public static final int DEFAULT_MAX_NODE_INSTRUCTION_WAIT_SECS = 300;

  private static final Logger log = LoggerFactory.getLogger(SolarSshPasswordAuthenticator.class);

  private final SolarSshService solarSshService;
  private final ActorDao actorDao;
  private String snHost = DEFAULT_SN_HOST;
  private int maxNodeInstructionWaitSecs = DEFAULT_MAX_NODE_INSTRUCTION_WAIT_SECS;

  /**
   * Constructor.
   * 
   * @param solarSshService
   *        the SolarSSH service
   * @param actorDao
   *        the authentication DAO
   */
  public SolarSshPasswordAuthenticator(SolarSshService solarSshService, ActorDao actorDao) {
    super();
    this.solarSshService = solarSshService;
    this.actorDao = actorDao;
  }

  @Override
  public boolean authenticate(String username, String password, ServerSession session)
      throws PasswordChangeRequiredException, AsyncAuthException {
    final DirectSshUsername directUsername;
    try {
      directUsername = DirectSshUsername.valueOf(username);
    } catch (IllegalArgumentException e) {
      log.debug("Username [{}] is not a valid direct username.", username);
      return false;
    }
    final Long nodeId = directUsername.getNodeId();
    final String tokenId = directUsername.getTokenId();
    SshSession sshSession = null;
    Actor actor = actorDao.getAuthenticatedActor(nodeId, tokenId, password);
    if (actor != null) {
      Date now = new Date();
      AuthorizationV2Builder authBuilder = new AuthorizationV2Builder(tokenId)
          .saveSigningKey(password).date(now).host(snHost)
          .path("/solaruser/api/v1/sec/instr/viewPending")
          .queryParams(singletonMap("nodeId", nodeId.toString()));
      Map<String, String> instructionParams = null;
      try {
        sshSession = solarSshService.createNewSession(nodeId, now.getTime(), authBuilder.build());
        sshSession.setDirectServerSession(session);

        instructionParams = SolarNetClient.createRemoteSshInstructionParams(sshSession);
        // CHECKSTYLE OFF: LineLength
        log.info(
            "Authenticated token {} for node {}; requesting node to connect to SolarSSH with parameters {}",
            tokenId, nodeId, instructionParams);
        // CHECKSTYLE ON: LineLength
        instructionParams.put("nodeId", nodeId.toString());
        instructionParams.put("topic", INSTRUCTION_TOPIC_START_REMOTE_SSH);
        now = new Date();
        authBuilder.reset().method(HttpMethod.POST).date(now).host(snHost).method(HttpMethod.POST)
            .path("/solaruser/api/v1/sec/instr/add")
            .contentType(MediaType.APPLICATION_FORM_URLENCODED_VALUE)
            .queryParams(instructionParams);
        sshSession = solarSshService.startSession(sshSession.getId(), now.getTime(),
            authBuilder.build());
        return waitForNodeInstructionToComplete(tokenId, INSTRUCTION_TOPIC_START_REMOTE_SSH,
            sshSession.getStartInstructionId(), authBuilder);
      } catch (IOException e) {
        log.info("Communication error creating new SshSession: {}", e.toString());
        // if we started the node remote SSH, stop it now
        if (sshSession != null) {
          instructionParams.put("topic", INSTRUCTION_TOPIC_STOP_REMOTE_SSH);
          now = new Date();
          authBuilder.reset().method(HttpMethod.POST).date(now).host(snHost).method(HttpMethod.POST)
              .path("/solaruser/api/v1/sec/instr/add")
              .contentType(MediaType.APPLICATION_FORM_URLENCODED_VALUE)
              .queryParams(instructionParams);
          try {
            solarSshService.stopSession(sshSession.getId(), now.getTime(), authBuilder.build());
          } catch (IOException e2) {
            // ignore
          }
          log.info("Issued {} instruction for token {} node {} with parameters {}",
              INSTRUCTION_TOPIC_STOP_REMOTE_SSH, tokenId, nodeId, instructionParams);
        }
        return false;
      }
    }
    return false;
  }

  private boolean waitForNodeInstructionToComplete(String tokenId, String topic, Long instructionId,
      AuthorizationV2Builder authBuilder) throws IOException {
    final long expire = System.currentTimeMillis() + (1000L * this.maxNodeInstructionWaitSecs);
    while (System.currentTimeMillis() < expire) {
      Date now = new Date();
      authBuilder.reset().date(now).host(snHost).path("/solaruser/api/v1/sec/instr/view")
          .queryParams(singletonMap("id", instructionId.toString()));
      SolarNodeInstructionState state = solarSshService.getInstructionState(instructionId,
          now.getTime(), authBuilder.build());
      if (state == SolarNodeInstructionState.Completed) {
        log.info("Token {} {} instruction {} completed", tokenId, topic, instructionId);
        // wait a few ticks for node SSH connection to actually be established
        try {
          Thread.sleep(1000L);
        } catch (InterruptedException e) {
          break;
        }
        return true;
      } else if (state == SolarNodeInstructionState.Declined) {
        return false;
      }
      // wait a few ticks
      try {
        Thread.sleep(1000L);
      } catch (InterruptedException e) {
        break;
      }
    }
    return false;
  }

  /**
   * Get the configured SolarNetwork host.
   * 
   * @return the host; defaults to {@link net.solarnetwork.solarssh.Globals#DEFAULT_SN_HOST}
   */
  public String getSnHost() {
    return snHost;
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
   * Set the maximum number of seconds to wait for a node instruction to complete.
   * 
   * @param maxNodeInstructionWaitSecs
   *        the maximum seconds; defaults to {@link #DEFAULT_MAX_NODE_INSTRUCTION_WAIT_SECS}
   */
  public void setMaxNodeInstructionWaitSecs(int maxNodeInstructionWaitSecs) {
    this.maxNodeInstructionWaitSecs = maxNodeInstructionWaitSecs;
  }

}
