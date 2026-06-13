package com.lynq.iam.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.sameInstance;

@ExtendWith(MockitoExtension.class)
class RedisConfigTest {

  private static final Class<StringRedisSerializer> EXPECTED_SERIALIZER_TYPE = StringRedisSerializer.class;

  @Mock
  private RedisConnectionFactory redisConnectionFactory;

  private RedisConfig redisConfig;

  @BeforeEach
  void setUp() {
    redisConfig = new RedisConfig();
  }

  @Test
  void redisTemplateReturnsNonNullRedisTemplate() {
    RedisTemplate<String, String> template = redisConfig.redisTemplate(redisConnectionFactory);

    assertThat(template, is(notNullValue()));
  }

  @Test
  void redisTemplateUsesProvidedConnectionFactory() {
    RedisTemplate<String, String> template = redisConfig.redisTemplate(redisConnectionFactory);

    assertThat(template.getConnectionFactory(), is(sameInstance(redisConnectionFactory)));
  }

  @Test
  void redisTemplateSetsStringRedisSerializerAsKeySerializer() {
    RedisTemplate<String, String> template = redisConfig.redisTemplate(redisConnectionFactory);

    assertThat(template.getKeySerializer(), is(instanceOf(EXPECTED_SERIALIZER_TYPE)));
  }

  @Test
  void redisTemplateSetsStringRedisSerializerAsValueSerializer() {
    RedisTemplate<String, String> template = redisConfig.redisTemplate(redisConnectionFactory);

    assertThat(template.getValueSerializer(), is(instanceOf(EXPECTED_SERIALIZER_TYPE)));
  }

  @Test
  void redisTemplateSetsStringRedisSerializerAsHashKeySerializer() {
    RedisTemplate<String, String> template = redisConfig.redisTemplate(redisConnectionFactory);

    assertThat(template.getHashKeySerializer(), is(instanceOf(EXPECTED_SERIALIZER_TYPE)));
  }

  @Test
  void redisTemplateSetsStringRedisSerializerAsHashValueSerializer() {
    RedisTemplate<String, String> template = redisConfig.redisTemplate(redisConnectionFactory);

    assertThat(template.getHashValueSerializer(), is(instanceOf(EXPECTED_SERIALIZER_TYPE)));
  }
}