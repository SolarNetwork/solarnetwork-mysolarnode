/* ==================================================================
 * WebMvcConfig.java - 17/06/2017 8:31:48 AM
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

package net.solarnetwork.solarssh.web.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import net.solarnetwork.solarssh.web.SolarSshHttpProxyController;

/**
 * WebMVC configuration.
 * 
 * @author matt
 * @version 1.1
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

  @Autowired
  private SolarSshHttpProxyController httpProxyController;

  @Scheduled(fixedDelayString = "${ssh.sessionProxyExpireCleanupJobMs:60000}")
  public void cleanupExpiredSessions() {
    httpProxyController.cleanupExpiredSessions();
  }

  @Override
  public void addCorsMappings(CorsRegistry registry) {
    registry.addMapping("/**").allowCredentials(false).allowedOrigins("*").allowedHeaders("*")
        .allowedMethods("*");
  }

}
