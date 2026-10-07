package com.lynq.bff.ratelimit;

import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("lynq.rate-limit")
public record RateLimitProperties(Map<RateLimitTier, Limit> tiers) {

  public Limit limitOf(RateLimitTier tier) {
    Limit limit = tiers == null ? null : tiers.get(tier);
    if (limit == null) {
      throw new IllegalStateException("No rate limit configured for tier " + tier);
    }
    return limit;
  }

  public record Limit(long perMinute, long perDay) {
  }
}
