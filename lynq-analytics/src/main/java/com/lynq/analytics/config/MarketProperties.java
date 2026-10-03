package com.lynq.analytics.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "lynq.analytics.market")
public record MarketProperties(
    @DefaultValue("10") int topSkills,
    @DefaultValue("12") int weeks,
    @DefaultValue("5") int minSample,
    @DefaultValue({"ARS", "USD"}) List<String> currencies) {
}
