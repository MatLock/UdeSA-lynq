package com.lynq.analytics.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "lynq.analytics.similarity")
public record SimilarityProperties(
    @DefaultValue("true") boolean includeSynthetic,
    @DefaultValue("2") int thresholdTags,
    @DefaultValue("1") int fallbackThresholdTags,
    @DefaultValue("5") int minSample) {
}
