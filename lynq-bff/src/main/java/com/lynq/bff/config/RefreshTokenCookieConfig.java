package com.lynq.bff.config;

import com.lynq.bff.security.RefreshTokenCookie;
import com.lynq.bff.security.RefreshTokenCookieProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(RefreshTokenCookieProperties.class)
public class RefreshTokenCookieConfig {

  @Bean
  public RefreshTokenCookie refreshTokenCookie(
      RefreshTokenCookieProperties properties,
      @Value("${server.servlet.context-path:}") String contextPath) {
    return new RefreshTokenCookie(properties, contextPath);
  }
}
