package com.lynq.analytics.config;

import com.lynq.analytics.cache.AnalyticsCaches;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.LoggingCacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;

@Configuration
@EnableCaching
@EnableConfigurationProperties(CacheProperties.class)
public class CacheConfig implements CachingConfigurer {

  public static final String KEY_PREFIX = "lynq-analytics::";

  @Bean
  public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory,
      CacheProperties properties) {
    RedisCacheConfiguration configuration = RedisCacheConfiguration.defaultCacheConfig()
        .entryTtl(properties.ttl())
        .disableCachingNullValues()
        .computePrefixWith(cacheName -> KEY_PREFIX + cacheName + "::")
        .serializeValuesWith(SerializationPair.fromSerializer(valueSerializer()));
    RedisCacheWriter cacheWriter = RedisCacheWriter.create(connectionFactory,
        writer -> writer.immediateWrites().collectStatistics());
    return RedisCacheManager.builder(cacheWriter)
        .cacheDefaults(configuration)
        .initialCacheNames(AnalyticsCaches.ALL)
        .disableCreateOnMissingCache()
        .build();
  }

  @Override
  public CacheErrorHandler errorHandler() {
    return new LoggingCacheErrorHandler();
  }

  static GenericJacksonJsonRedisSerializer valueSerializer() {
    return GenericJacksonJsonRedisSerializer.builder()
        .enableDefaultTyping(BasicPolymorphicTypeValidator.builder()
            .allowIfSubType("com.lynq.analytics.")
            .allowIfSubType("java.util.")
            .allowIfSubType("java.lang.")
            .allowIfSubType("java.time.")
            .build())
        .build();
  }
}
