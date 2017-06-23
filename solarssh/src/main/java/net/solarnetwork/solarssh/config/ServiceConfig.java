/* ==================================================================
 * ServiceConfig.java - 16/06/2017 7:34:47 PM
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

package net.solarnetwork.solarssh.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import net.solarnetwork.solarssh.service.DefaultSolarNetClient;
import net.solarnetwork.solarssh.service.DefaultSolarSshService;
import net.solarnetwork.solarssh.service.DefaultSolarSshdService;
import net.solarnetwork.solarssh.service.SolarNetClient;
import net.solarnetwork.solarssh.service.SolarSshService;

/**
 * Main service configuration.
 * 
 * @author matt
 * @version 1.0
 */
@Configuration
@PropertySource("classpath:application.properties")
@PropertySource("classpath:application-test.properties")
@EnableScheduling
public class ServiceConfig {

  @Value("${ssh.host:ssh.solarnetwork.net}")
  private String sshHost = "ssh.host:ssh.solarnetwork.net";

  @Value("${ssh.port:8022}")
  private int sshPort = 8022;

  @Value("${ssh.keyResource:classpath:/sshd-server-key}")
  private Resource sshKeyResource = new ClassPathResource("/sshd-server-key");

  @Value("${ssh.keyPassword:#{null}}")
  private String sshKeyPassword = null;

  @Value("${ssh.reversePort.min:50000}")
  private int sshReversePortMin = 50000;

  @Value("${ssh.reversePort.max:65000}")
  private int sshReversePortMax = 65000;

  @Value("${ssh.sessionExpireSeconds:300}")
  private int sessionExpireSeconds = 300;

  @Value("${solarnet.baseUrl:https://data.solarnetwork.net}")
  private String solarNetBaseUrl = "https://data.solarnetwork.net";

  /**
   * Initialize the {@link SolarSshService} service.
   * 
   * @return the service
   */
  @Bean
  public DefaultSolarSshService solarSshService() {
    DefaultSolarSshService service = new DefaultSolarSshService(solarNetClient());
    service.setHost(sshHost);
    service.setPort(sshPort);
    service.setMinPort(sshReversePortMin);
    service.setMaxPort(sshReversePortMax);
    service.setSessionExpireSeconds(sessionExpireSeconds);
    return service;
  }

  @Scheduled(fixedDelayString = "${ssh.sessionExpireCleanupJobMs:60000}")
  public void cleanupExpiredSessions() {
    solarSshService().cleanupExpiredSessions();
  }

  @Bean
  public static PropertySourcesPlaceholderConfigurer placeHolderConfigurer() {
    return new PropertySourcesPlaceholderConfigurer();
  }

  /**
   * Initialize the SolarNetClient.
   * 
   * @return the client
   */
  @Bean
  public SolarNetClient solarNetClient() {
    DefaultSolarNetClient client = new DefaultSolarNetClient();
    client.setApiBaseUrl(solarNetBaseUrl);
    return client;
  }

  /**
   * Initialize the SSHD server service.
   * 
   * @return the service.
   */
  @Bean(initMethod = "start", destroyMethod = "stop")
  public DefaultSolarSshdService solarSshdService() {
    DefaultSolarSshdService service = new DefaultSolarSshdService(solarSshService());
    service.setPort(sshPort);
    service.setServerKeyResource(sshKeyResource);
    service.setServerKeyPassword(sshKeyPassword);
    return service;
  }

}
