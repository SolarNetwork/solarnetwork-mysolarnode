/* ==================================================================
 * SolarSshService.java - 16/06/2017 5:10:34 PM
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

import net.solarnetwork.solarssh.dao.SshSessionDao;
import net.solarnetwork.solarssh.domain.SshCredentials;
import net.solarnetwork.solarssh.domain.SshSession;
import net.solarnetwork.solarssh.domain.SshTerminalSettings;

/**
 * API for the SolarSSH service.
 * 
 * @author matt
 * @version 1.0
 */
public interface SolarSshService extends SshSessionDao {

  /**
   * Get a new session with an unused reverse SSH port.
   * 
   * <p>
   * The validity of this session is for only a short amount of time, if it is not subsequently
   * connected to a SSH pipe.
   * </p>
   * 
   * @param nodeId
   *        the SolarNode ID to instruct
   * @param authorizationDate
   *        the authorization date used in {@code authorization}
   * @param authorization
   *        the {@code Authorization} HTTP header value to use
   * @return a new session instance
   */
  SshSession createNewSession(Long nodeId, long authorizationDate, String authorization)
      throws IOException;

  SshSession startSession(String sessionId, long authorizationDate, String authorization)
      throws IOException;

  SshSession attachTerminal(String sessionId, long authorizationDate, String authorization,
      SshCredentials nodeCredentials, SshTerminalSettings settings, InputStream in,
      OutputStream out) throws IOException;

  SshSession stopSession(String sessionId, long authorizationDate, String authorization)
      throws IOException;
}
