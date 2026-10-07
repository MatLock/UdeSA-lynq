package com.lynq.bff.ratelimit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.bucket4j.ConsumptionProbe;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RateLimiterTest {

  private static final String JANE = "11111111-1111-1111-1111-111111111111";
  private static final String JOHN = "22222222-2222-2222-2222-222222222222";

  private final RateLimiter rateLimiter = new RateLimiter(new RateLimitProperties(Map.of(
      RateLimitTier.LIGHT, new RateLimitProperties.Limit(3, 100),
      RateLimitTier.STANDARD, new RateLimitProperties.Limit(10, 2),
      RateLimitTier.HEAVY, new RateLimitProperties.Limit(1, 100))));

  @Test
  void letsTheCallerThroughUntilTheMinuteQuotaRunsOut() {
    assertThat(rateLimiter.tryConsume(RateLimitTier.LIGHT, JANE).getRemainingTokens(), is(2L));
    assertThat(rateLimiter.tryConsume(RateLimitTier.LIGHT, JANE).getRemainingTokens(), is(1L));
    assertThat(rateLimiter.tryConsume(RateLimitTier.LIGHT, JANE).getRemainingTokens(), is(0L));

    ConsumptionProbe rejected = rateLimiter.tryConsume(RateLimitTier.LIGHT, JANE);

    assertThat(rejected.isConsumed(), is(false));
    assertThat(rejected.getNanosToWaitForRefill(), is(greaterThan(0L)));
  }

  @Test
  void theDayQuotaCapsTheCallerEvenWithMinuteQuotaLeft() {
    rateLimiter.tryConsume(RateLimitTier.STANDARD, JANE);
    rateLimiter.tryConsume(RateLimitTier.STANDARD, JANE);

    assertThat(rateLimiter.tryConsume(RateLimitTier.STANDARD, JANE).isConsumed(), is(false));
  }

  @Test
  void eachCallerHasTheirOwnQuota() {
    rateLimiter.tryConsume(RateLimitTier.HEAVY, JANE);

    assertThat(rateLimiter.tryConsume(RateLimitTier.HEAVY, JANE).isConsumed(), is(false));
    assertThat(rateLimiter.tryConsume(RateLimitTier.HEAVY, JOHN).isConsumed(), is(true));
  }

  @Test
  void eachTierHasItsOwnQuota() {
    rateLimiter.tryConsume(RateLimitTier.HEAVY, JANE);

    assertThat(rateLimiter.tryConsume(RateLimitTier.HEAVY, JANE).isConsumed(), is(false));
    assertThat(rateLimiter.tryConsume(RateLimitTier.LIGHT, JANE).isConsumed(), is(true));
  }

  @Test
  void refusesToStartWithATierLeftUnconfigured() {
    RateLimitProperties properties = new RateLimitProperties(Map.of(
        RateLimitTier.LIGHT, new RateLimitProperties.Limit(3, 100)));

    IllegalStateException failure =
        assertThrows(IllegalStateException.class, () -> new RateLimiter(properties));

    assertThat(failure.getMessage(), is("No rate limit configured for tier STANDARD"));
  }
}
