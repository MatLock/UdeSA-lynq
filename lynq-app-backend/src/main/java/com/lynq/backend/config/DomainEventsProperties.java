package com.lynq.backend.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "lynq.events")
public record DomainEventsProperties(
    String topicArn,
    @DefaultValue("3") int publishAttempts,
    @DefaultValue("200ms") Duration retryBackoff) {
}
