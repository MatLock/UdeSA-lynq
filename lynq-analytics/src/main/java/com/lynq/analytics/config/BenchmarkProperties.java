package com.lynq.analytics.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "lynq.analytics.benchmark")
public record BenchmarkProperties(
    @DefaultValue("60") int reachThreshold,
    @DefaultValue("5") int minRelevantJobs,
    @DefaultValue("5") int minPeers,
    @DefaultValue("1") int relevanceThresholdTags,
    @DefaultValue("2") int peerThresholdTags,
    @DefaultValue("5") int skillUnlocks,
    @DefaultValue("2000") int pairsPerRequest,
    @DefaultValue("90") int seriesDays) {
}
