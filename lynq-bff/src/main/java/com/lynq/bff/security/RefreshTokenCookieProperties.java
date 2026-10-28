package com.lynq.bff.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("lynq.security.refresh-cookie")
public record RefreshTokenCookieProperties(boolean secure, Duration maxAge) {
}
