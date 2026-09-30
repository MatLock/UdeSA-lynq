package com.lynq.analytics.config;

import com.lynq.analytics.cache.AnalyticsCaches;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.interceptor.LoggingCacheErrorHandler;
import org.springframework.data.redis.cache.RedisCache;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class CacheConfigTest {

  private static final Duration ONE_HOUR = Duration.ofHours(1);

  private final CacheConfig cacheConfig = new CacheConfig();

  private RedisCacheManager cacheManager;

  @BeforeEach
  void setUp() {
    cacheManager = cacheConfig.cacheManager(mock(RedisConnectionFactory.class),
        new CacheProperties(ONE_HOUR));
    cacheManager.afterPropertiesSet();
  }

  @Test
  void hasOneCachePerEndpoint() {
    assertThat(cacheManager.getCacheNames(), containsInAnyOrder("time-to-fill", "standing",
        "salary"));
  }

  @Test
  void refusesACacheThatIsNotDeclared() {
    assertThat(cacheManager.getCache("time-to-fil"), is(nullValue()));
  }

  @Test
  void everyCacheKeepsItsEntriesForTheConfiguredTtl() {
    AnalyticsCaches.ALL.forEach(name -> assertThat(configuration(name).getTtlFunction()
        .getTimeToLive("77777777-7777-7777-7777-777777777777", "value"), is(ONE_HOUR)));
  }

  @Test
  void prefixesKeysWithTheServiceAndTheCache() {
    assertThat(configuration(AnalyticsCaches.STANDING).getKeyPrefixFor(AnalyticsCaches.STANDING),
        is("lynq-analytics::standing::"));
  }

  @Test
  void keepsStatisticsForTheMetrics() {
    assertThat(cacheManager.getCacheNames().stream()
        .map(name -> (RedisCache) cacheManager.getCache(name))
        .allMatch(cache -> cache.getStatistics() != null), is(true));
  }

  @Test
  void doesNotCacheNullValues() {
    assertThat(configuration(AnalyticsCaches.SALARY).getAllowCacheNullValues(), is(false));
  }

  @Test
  void roundTripsAResponseRecordWithItsType() {
    GenericJacksonJsonRedisSerializer serializer = CacheConfig.valueSerializer();
    Figure figure = new Figure("77777777-7777-7777-7777-777777777777", 21.5, 12,
        LocalDate.parse("2026-09-30"), List.of(10, 21, 30));

    assertThat(serializer.deserialize(serializer.serialize(figure)), is(figure));
  }

  @Test
  void refusesToReadATypeOutsideTheService() {
    GenericJacksonJsonRedisSerializer serializer = CacheConfig.valueSerializer();
    byte[] foreign = """
        {"@class": "java.net.InetSocketAddress", "hostString": "localhost", "port": 80}"""
        .getBytes(StandardCharsets.UTF_8);

    assertThrows(SerializationException.class, () -> serializer.deserialize(foreign));
  }

  @Test
  void aCacheFailureIsLoggedAndTheCallGoesOn() {
    assertThat(cacheConfig.errorHandler(), is(instanceOf(LoggingCacheErrorHandler.class)));
    assertDoesNotThrow(() -> cacheConfig.errorHandler().handleCacheGetError(
        new IllegalStateException("redis is down"), mock(Cache.class), "key"));
  }

  private RedisCacheConfiguration configuration(String name) {
    return cacheManager.getCacheConfigurations().get(name);
  }

  record Figure(String jobId, double median, int n, LocalDate computedOn, List<Integer> values) {
  }
}
