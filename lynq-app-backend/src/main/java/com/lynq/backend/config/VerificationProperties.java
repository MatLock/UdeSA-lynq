package com.lynq.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "lynq.verification")
public record VerificationProperties(
    @DefaultValue("20") int windowDays,
    @DefaultValue("10") int quotaPerCategory,
    @DefaultValue("25") int expireAfterDays) {
}
