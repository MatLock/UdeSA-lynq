package com.lynq.analytics.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "lynq.analytics.standing")
public record StandingProperties(@DefaultValue("5") int minApplicants) {
}
