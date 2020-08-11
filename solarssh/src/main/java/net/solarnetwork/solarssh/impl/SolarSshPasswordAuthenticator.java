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

import org.apache.sshd.server.auth.AsyncAuthException;
import org.apache.sshd.server.auth.password.PasswordAuthenticator;
import org.apache.sshd.server.auth.password.PasswordChangeRequiredException;
import org.apache.sshd.server.session.ServerSession;

import net.solarnetwork.solarssh.dao.SshSessionDao;

/**
 * {@link PasswordAuthenticator} for direct SolarSSH connections.
 * 
 * @author matt
 * @version 1.0
 */
public class SolarSshPasswordAuthenticator implements PasswordAuthenticator {

  private final SshSessionDao sessionDao;

  /**
   * Constructor.
   * 
   * @param sessionDao
   *        the DAO to access sessions with
   */
  public SolarSshPasswordAuthenticator(SshSessionDao sessionDao) {
    super();
    this.sessionDao = sessionDao;
  }

  @Override
  public boolean authenticate(String username, String password, ServerSession session)
      throws PasswordChangeRequiredException, AsyncAuthException {
    // username/password is a SolarNetwork authentication token
    // TODO Auto-generated method stub
    return false;
  }

}
