package com.lynq.analytics.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "lynq.analytics.cache")
public record CacheProperties(@DefaultValue("PT1H") Duration ttl) {
}
