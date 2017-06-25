/* ==================================================================
 * SshSessionProxyServlet.java - 24/06/2017 5:03:03 PM
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

import java.util.Collections;
import java.util.Enumeration;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;

import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicHeader;
import org.mitre.dsmiley.httpproxy.ProxyServlet;

import net.solarnetwork.solarssh.domain.SshSession;

/**
 * Extension of {@link ProxyServlet} to associate with a specific {@link SshSession}.
 * 
 * @author matt
 * @version 1.0
 */
public class SshSessionProxyServlet extends ProxyServlet {

  private static final long serialVersionUID = 1273522570866832919L;

  private final SshSession session;
  private final ServletConfig servletConfig;
  private final String proxyPath;

  private static class StaticServletConfig implements ServletConfig {

    @Override
    public String getServletName() {
      return "SshSessionProxyServlet";
    }

    @Override
    public ServletContext getServletContext() {
      return null;
    }

    @Override
    public Enumeration<String> getInitParameterNames() {
      return null;
    }

    @Override
    public String getInitParameter(String name) {
      return null;
    }
  }

  private static final ServletConfig GLOBAL_SERVLET_CONFIG = new StaticServletConfig();

  /**
   * Constructor.
   */
  public SshSessionProxyServlet(SshSession session, String proxyPath) {
    super();
    this.session = session;
    this.proxyPath = proxyPath;
    this.servletConfig = GLOBAL_SERVLET_CONFIG;
  }

  @Override
  public ServletConfig getServletConfig() {
    return servletConfig;
  }

  @Override
  protected HttpClient createHttpClient(RequestConfig requestConfig) {
    return HttpClientBuilder.create().setDefaultRequestConfig(requestConfig).setDefaultHeaders(
        Collections.singletonList(new BasicHeader("X-Forwarded-Path", proxyPath))).build();
  }

  @Override
  protected String getTargetUri(HttpServletRequest servletRequest) {
    String targetUri = (String) servletRequest.getAttribute(ATTR_TARGET_URI);
    String requestUri = servletRequest.getRequestURI();
    if (requestUri.startsWith(proxyPath) && requestUri.length() > proxyPath.length()) {
      targetUri += requestUri.substring(proxyPath.length() - 1);
    }
    return targetUri;
  }

  @Override
  protected String rewriteUrlFromResponse(HttpServletRequest servletRequest, String theUrl) {
    int redirectUrlPos = theUrl.indexOf("://");
    if (redirectUrlPos >= 0) {
      redirectUrlPos = theUrl.indexOf("/", redirectUrlPos + 3);
    }
    if (redirectUrlPos < 0) {
      redirectUrlPos = 0;
    }

    StringBuffer curUrl = servletRequest.getRequestURL();
    int pos = curUrl.indexOf("://");
    if (pos >= 0) {
      if ((pos = curUrl.indexOf("/", pos + 3)) >= 0) {
        curUrl.setLength(pos);
      }
    }
    if (!theUrl.startsWith(proxyPath, redirectUrlPos)) {
      curUrl.append(proxyPath);
    }
    curUrl.append(theUrl, redirectUrlPos, theUrl.length());
    theUrl = curUrl.toString();

    return theUrl;
  }

  @Override
  protected String getConfigParam(String key) {
    switch (key) {
      case ProxyServlet.P_TARGET_URI:
        return "http://127.0.0.1:" + session.getReverseHttpPort();

      case ProxyServlet.P_CONNECTTIMEOUT:
        return "30000";

      default:
        return null;
    }
  }

}
