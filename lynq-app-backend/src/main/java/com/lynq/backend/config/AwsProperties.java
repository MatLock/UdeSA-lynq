package com.lynq.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "lynq.aws")
public record AwsProperties(
    @DefaultValue("us-east-1") String region,
    @DefaultValue("") String accessKeyId,
    @DefaultValue("") String secretAccessKey,
    @DefaultValue("") String endpoint) {
}
