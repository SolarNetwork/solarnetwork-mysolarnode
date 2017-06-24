/* ==================================================================
 * SolarSshHttpProxyController.java - 24/06/2017 5:17:37 PM
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.mitre.dsmiley.httpproxy.ProxyServlet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import net.solarnetwork.solarssh.AuthorizationException;
import net.solarnetwork.solarssh.dao.SshSessionDao;
import net.solarnetwork.solarssh.domain.SshSession;

/**
 * Proxy controller for SolarNode over a reverse SSH tunnel.
 * 
 * @author matt
 * @version 1.0
 */
@CrossOrigin
@Controller
public class SolarSshHttpProxyController {

  private final SshSessionDao sessionDao;
  private final ConcurrentMap<String, ProxyServlet> sessionProxyMap = new ConcurrentHashMap<>();

  private static final Logger LOG = LoggerFactory.getLogger(SolarSshHttpProxyController.class);

  public SolarSshHttpProxyController(SshSessionDao sessionDao) {
    super();
    this.sessionDao = sessionDao;
  }

  /**
   * Proxy a HTTP request to the SolarNode associated with a session.
   */
  @RequestMapping(value = "/nodeproxy/{sessionId}/**", method = { RequestMethod.DELETE,
      RequestMethod.GET, RequestMethod.HEAD, RequestMethod.OPTIONS, RequestMethod.PATCH,
      RequestMethod.POST, RequestMethod.PUT, RequestMethod.TRACE })
  public void nodeProxy(@PathVariable("sessionId") String sessionId, HttpServletRequest req,
      HttpServletResponse resp) throws IOException, ServletException {
    ProxyServlet proxy = sessionProxyMap.computeIfAbsent(sessionId, k -> {
      SshSession session = sessionDao.findOne(sessionId);
      if (session == null) {
        throw new AuthorizationException("SshSession not available");
      }
      SshSessionProxyServlet s = new SshSessionProxyServlet(session,
          req.getContextPath() + "/nodeproxy/" + sessionId);
      try {
        s.init();
      } catch (ServletException e) {
        throw new RuntimeException(e);
      }
      return s;
    });
    LOG.debug("Context path: {}; requestURI: {}", req.getContextPath(), req.getRequestURI());
    proxy.service(req, resp);
  }

  /**
   * Handle an authorization error.
   * 
   * @param e
   *        the exception
   * @param resp
   *        the response
   */
  @ExceptionHandler(AuthorizationException.class)
  public void ioException(AuthorizationException e, HttpServletResponse resp) {
    try {
      resp.sendError(HttpStatus.FORBIDDEN.value(), e.getMessage());
    } catch (IOException e2) {
      // ignore
    }
  }

}
