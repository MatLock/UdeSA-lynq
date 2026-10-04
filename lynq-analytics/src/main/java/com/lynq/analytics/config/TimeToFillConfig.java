package com.lynq.analytics.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(TimeToFillProperties.class)
public class TimeToFillConfig {
}
