package com.lynq.analytics;

import com.lynq.analytics.cache.AnalyticsCaches;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.LocalDate;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.nullValue;

class CacheApplicationTests extends AbstractE2ETest {

  private static final String JOB_ID = "77777777-7777-7777-7777-777777777777";
  private static final String USER_ID = "11111111-1111-1111-1111-111111111111";
  private static final String OTHER_USER_ID = "22222222-2222-2222-2222-222222222222";

  @Autowired
  private CacheManager cacheManager;

  @Autowired
  private StringRedisTemplate redisTemplate;

  @Autowired
  private MeterRegistry meterRegistry;

  @BeforeEach
  void setUp() {
    Set<String> keys = redisTemplate.keys("lynq-analytics::*");
    if (!keys.isEmpty()) {
      redisTemplate.delete(keys);
    }
  }

  @Test
  void storesAnEntryInRedisUnderTheServiceTheCacheAndTheJobIdForAnHour() {
    Figure figure = new Figure(JOB_ID, 21.0, 12, LocalDate.parse("2026-09-30"));

    cache(AnalyticsCaches.TIME_TO_FILL).put(JOB_ID, figure);

    String key = "lynq-analytics::time-to-fill::" + JOB_ID;
    assertThat(redisTemplate.hasKey(key), is(true));
    assertThat(redisTemplate.getExpire(key, TimeUnit.SECONDS),
        is(allOf(greaterThan(3590L), lessThanOrEqualTo(3600L))));
    assertThat(cache(AnalyticsCaches.TIME_TO_FILL).get(JOB_ID, Figure.class), is(figure));
  }

  @Test
  void keepsTheStandingOfEachCandidateApart() {
    Figure mine = new Figure(JOB_ID, 72.0, 30, LocalDate.parse("2026-09-30"));
    Figure theirs = new Figure(JOB_ID, 40.0, 30, LocalDate.parse("2026-09-30"));

    cache(AnalyticsCaches.STANDING).put(JOB_ID + ":" + USER_ID, mine);
    cache(AnalyticsCaches.STANDING).put(JOB_ID + ":" + OTHER_USER_ID, theirs);

    assertThat(cache(AnalyticsCaches.STANDING).get(JOB_ID + ":" + USER_ID, Figure.class),
        is(mine));
    assertThat(cache(AnalyticsCaches.STANDING).get(JOB_ID + ":" + OTHER_USER_ID, Figure.class),
        is(theirs));
    assertThat(cache(AnalyticsCaches.STANDING).get(JOB_ID), is(nullValue()));
  }

  @Test
  void evictsAnEntry() {
    cache(AnalyticsCaches.SALARY).put(JOB_ID, new Figure(JOB_ID, 1_500_000.0, 8,
        LocalDate.parse("2026-09-30")));

    cache(AnalyticsCaches.SALARY).evict(JOB_ID);

    assertThat(redisTemplate.hasKey("lynq-analytics::salary::" + JOB_ID), is(false));
  }

  @Test
  void countsHitsAndMissesAsMetricsPerCache() {
    double hits = gets(AnalyticsCaches.SALARY, "hit");
    double misses = gets(AnalyticsCaches.SALARY, "miss");

    cache(AnalyticsCaches.SALARY).get(JOB_ID);
    cache(AnalyticsCaches.SALARY).put(JOB_ID, new Figure(JOB_ID, 1_500_000.0, 8,
        LocalDate.parse("2026-09-30")));
    cache(AnalyticsCaches.SALARY).get(JOB_ID);

    assertThat(gets(AnalyticsCaches.SALARY, "miss"), is(misses + 1));
    assertThat(gets(AnalyticsCaches.SALARY, "hit"), is(hits + 1));
  }

  @Test
  void hasNoCacheOutsideTheDeclaredOnes() {
    assertThat(cacheManager.getCache("trends"), is(nullValue()));
  }

  private double gets(String cache, String result) {
    return meterRegistry.get("cache.gets").tag("cache", cache).tag("result", result)
        .functionCounter().count();
  }

  private Cache cache(String name) {
    return cacheManager.getCache(name);
  }

  record Figure(String jobId, double median, int n, LocalDate computedOn) {
  }
}
