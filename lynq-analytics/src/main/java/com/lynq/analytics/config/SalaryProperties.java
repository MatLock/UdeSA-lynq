package com.lynq.analytics.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "lynq.analytics.salary")
public record SalaryProperties(
    @DefaultValue("5") int minSample,
    @DefaultValue("ARS") String defaultCurrency) {
}
