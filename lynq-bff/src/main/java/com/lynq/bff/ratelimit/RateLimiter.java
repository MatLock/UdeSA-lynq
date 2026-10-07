package com.lynq.bff.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import java.time.Duration;

public class RateLimiter {

  private static final Duration MINUTE = Duration.ofMinutes(1);
  private static final Duration DAY = Duration.ofDays(1);
  private static final long MAX_TRACKED_BUCKETS = 100_000;

  private final RateLimitProperties properties;
  private final Cache<String, Bucket> buckets;

  public RateLimiter(RateLimitProperties properties) {
    for (RateLimitTier tier : RateLimitTier.values()) {
      properties.limitOf(tier);
    }
    this.properties = properties;
    this.buckets = Caffeine.newBuilder()
        .expireAfterAccess(DAY)
        .maximumSize(MAX_TRACKED_BUCKETS)
        .build();
  }

  public ConsumptionProbe tryConsume(RateLimitTier tier, String userId) {
    return buckets.get(tier.name() + ":" + userId, key -> newBucket(tier))
        .tryConsumeAndReturnRemaining(1);
  }

  private Bucket newBucket(RateLimitTier tier) {
    RateLimitProperties.Limit limit = properties.limitOf(tier);
    return Bucket.builder()
        .addLimit(Bandwidth.builder()
            .capacity(limit.perMinute())
            .refillGreedy(limit.perMinute(), MINUTE)
            .build())
        .addLimit(Bandwidth.builder()
            .capacity(limit.perDay())
            .refillGreedy(limit.perDay(), DAY)
            .build())
        .build();
  }
}
