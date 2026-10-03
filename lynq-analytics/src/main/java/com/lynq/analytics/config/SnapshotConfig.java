package com.lynq.analytics.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({BenchmarkProperties.class, MarketProperties.class})
public class SnapshotConfig {

  @Bean
  public Clock snapshotClock(
      @Value("${lynq.analytics.snapshot.zone:America/Argentina/Buenos_Aires}") String zone) {
    return Clock.system(ZoneId.of(zone));
  }
}
