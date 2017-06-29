/* ==================================================================
 * SolarSshController.java - 16/06/2017 4:36:32 PM
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

import static net.solarnetwork.solarssh.web.WebConstants.PRESIGN_AUTHORIZATION_HEADER;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import net.solarnetwork.solarssh.AuthorizationException;
import net.solarnetwork.solarssh.domain.SshSession;
import net.solarnetwork.solarssh.service.SolarSshService;
import net.solarnetwork.web.domain.Response;
import net.solarnetwork.web.security.WebConstants;

/**
 * Web controller for connection commands.
 * 
 * @author matt
 * @version 1.0
 */
@RestController
@CrossOrigin
@RequestMapping("/api/v1/ssh")
public class SolarSshController {

  private final SolarSshService solarSshService;

  @Autowired
  public SolarSshController(SolarSshService solarSshService) {
    super();
    this.solarSshService = solarSshService;
  }

  /**
   * Request an unused reverse SSH port.
   * 
   * @param nodeId
   *        the node ID to create the session for
   * @param request
   *        the request
   * @return the created session
   * @throws IOException
   *         if any communication error occurs
   */
  @RequestMapping(value = "/session/new", method = RequestMethod.GET)
  public Response<SshSession> createNewSession(@RequestParam("nodeId") Long nodeId,
      HttpServletRequest request) throws IOException {
    long authorizationDate = request.getDateHeader(WebConstants.HEADER_DATE);
    String preSignedAuthorization = request.getHeader(PRESIGN_AUTHORIZATION_HEADER);
    SshSession session = solarSshService.createNewSession(nodeId, authorizationDate,
        preSignedAuthorization);
    return Response.response(session);
  }

  /**
   * Issue a {@literal StartRemoteSsh} instruction and start the reverse SSH connection.
   * 
   * @param sessionId
   *        the SSH session ID
   * @param request
   *        the request
   * @return the updated session
   * @throws IOException
   *         if any communication error occurs
   */
  @RequestMapping(value = "/session/{sessionId}/start", method = RequestMethod.GET)
  public Response<SshSession> startSession(@PathVariable("sessionId") String sessionId,
      HttpServletRequest request) throws IOException {
    long authorizationDate = request.getDateHeader(WebConstants.HEADER_DATE);
    String preSignedAuthorization = request.getHeader(PRESIGN_AUTHORIZATION_HEADER);
    SshSession session = solarSshService.startSession(sessionId, authorizationDate,
        preSignedAuthorization);
    return Response.response(session);
  }

  /**
   * Issue a {@literal StopRemoteSsh} instruction and stop the reverse SSH connection.
   * 
   * @param sessionId
   *        the SSH session ID
   * @param request
   *        the request
   * @return the updated session
   * @throws IOException
   *         if any communication error occurs
   */
  @RequestMapping(value = "/session/{sessionId}/stop", method = RequestMethod.GET)
  public Response<SshSession> stopSession(@PathVariable("sessionId") String sessionId,
      HttpServletRequest request) throws IOException {
    long authorizationDate = request.getDateHeader(WebConstants.HEADER_DATE);
    String preSignedAuthorization = request.getHeader(PRESIGN_AUTHORIZATION_HEADER);
    SshSession session = solarSshService.stopSession(sessionId, authorizationDate,
        preSignedAuthorization);
    return Response.response(session);
  }

  /**
   * Handle an IOException.
   * 
   * @param e
   *        the exception
   * @return the response
   */
  @ExceptionHandler(IOException.class)
  public ResponseEntity<Response<Object>> ioException(IOException e) {
    return new ResponseEntity<Response<Object>>(
        new Response<Object>(Boolean.FALSE, "570", e.getMessage(), null), HttpStatus.BAD_GATEWAY);
  }

  /**
   * Handle an AuthorizationException.
   * 
   * @param e
   *        the exception
   * @return the response
   */
  @ExceptionHandler(AuthorizationException.class)
  public ResponseEntity<Response<Object>> ioException(AuthorizationException e) {
    return new ResponseEntity<Response<Object>>(
        new Response<Object>(Boolean.FALSE, "570", e.getMessage(), null), HttpStatus.FORBIDDEN);
  }

}
