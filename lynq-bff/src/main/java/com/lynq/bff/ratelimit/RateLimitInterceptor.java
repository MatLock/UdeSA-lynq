package com.lynq.bff.ratelimit;

import com.lynq.bff.exceptions.TooManyRequestsException;
import com.lynq.bff.security.LynqUserPrincipal;
import io.github.bucket4j.ConsumptionProbe;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.concurrent.TimeUnit;
import lombok.extern.log4j.Log4j2;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

@Log4j2
public class RateLimitInterceptor implements HandlerInterceptor {

  public static final String REMAINING_HEADER = "X-RateLimit-Remaining";
  public static final String REJECTED_METRIC = "lynq.bff.rate.limit.rejected";

  private static final String RATE_LIMIT_EXCEEDED =
      "Too many requests, try again in %d seconds";

  private final RateLimiter rateLimiter;
  private final MeterRegistry meterRegistry;

  public RateLimitInterceptor(RateLimiter rateLimiter, MeterRegistry meterRegistry) {
    this.rateLimiter = rateLimiter;
    this.meterRegistry = meterRegistry;
  }

  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                           Object handler) {
    if (!(handler instanceof HandlerMethod handlerMethod)) {
      return true;
    }
    RateLimited rateLimited = handlerMethod.getMethodAnnotation(RateLimited.class);
    if (rateLimited == null) {
      return true;
    }

    RateLimitTier tier = rateLimited.value();
    String userId = authenticatedUserId();
    ConsumptionProbe probe = rateLimiter.tryConsume(tier, userId);

    if (!probe.isConsumed()) {
      long retryAfterSeconds = retryAfterSeconds(probe);
      log.warn("message= Rate limit exceeded, user_id={}, tier={}, path={}, retry_after={}",
          userId, tier, request.getServletPath(), retryAfterSeconds);
      meterRegistry.counter(REJECTED_METRIC, "tier", tier.name()).increment();
      throw new TooManyRequestsException(
          String.format(RATE_LIMIT_EXCEEDED, retryAfterSeconds), retryAfterSeconds);
    }

    response.setHeader(REMAINING_HEADER, String.valueOf(probe.getRemainingTokens()));
    return true;
  }

  private static String authenticatedUserId() {
    LynqUserPrincipal principal = (LynqUserPrincipal) SecurityContextHolder.getContext()
        .getAuthentication()
        .getPrincipal();
    return principal.getId();
  }

  private static long retryAfterSeconds(ConsumptionProbe probe) {
    long nanos = probe.getNanosToWaitForRefill();
    long seconds = TimeUnit.NANOSECONDS.toSeconds(nanos);
    if (TimeUnit.SECONDS.toNanos(seconds) < nanos) {
      seconds++;
    }
    return Math.max(1, seconds);
  }
}
