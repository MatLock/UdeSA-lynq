package com.lynq.analytics.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "lynq.analytics.time-to-fill")
public record TimeToFillProperties(
    @DefaultValue("5") int minSample,
    @DefaultValue("25") int expiredAfterDays) {
}
