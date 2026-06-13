package com.lynq.iam.service;

import com.lynq.iam.service.RedisService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisServiceTest {

  private static final long REFRESH_TOKEN_EXPIRATION_DAYS = 30L;
  private static final String SAMPLE_USER_ID = "550e8400-e29b-41d4-a716-446655440000";
  private static final String SAMPLE_REFRESH_TOKEN = "sample-refresh-token-value";
  private static final String REDIS_REFRESH_KEY_PREFIX = "refresh:";
  private static final String EXPECTED_REDIS_KEY = REDIS_REFRESH_KEY_PREFIX + SAMPLE_REFRESH_TOKEN;
  private static final Duration EXPECTED_EXPIRATION = Duration.ofDays(REFRESH_TOKEN_EXPIRATION_DAYS);
  private static final String UNKNOWN_REFRESH_TOKEN = "missing-refresh-token";

  @Mock
  private RedisTemplate<String, String> redisTemplate;

  @Mock
  private ValueOperations<String, String> valueOperations;

  private RedisService redisService;

  @BeforeEach
  void setUp() {
    redisService = new RedisService(redisTemplate, REFRESH_TOKEN_EXPIRATION_DAYS);
  }

  @Test
  void saveRefreshTokenForUserWritesPrefixedKeyAndUserIdWithExpirationDuration() {
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);

    redisService.saveRefreshTokenForUser(SAMPLE_USER_ID, SAMPLE_REFRESH_TOKEN);

    verify(valueOperations).set(EXPECTED_REDIS_KEY, SAMPLE_USER_ID, EXPECTED_EXPIRATION);
  }

  @Test
  void findUserIdByRefreshTokenReturnsUserIdStoredAtPrefixedKey() {
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.get(EXPECTED_REDIS_KEY)).thenReturn(SAMPLE_USER_ID);

    String result = redisService.findUserIdByRefreshToken(SAMPLE_REFRESH_TOKEN);

    assertThat(result, is(SAMPLE_USER_ID));
  }

  @Test
  void findUserIdByRefreshTokenReturnsNullWhenKeyAbsent() {
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.get(REDIS_REFRESH_KEY_PREFIX + UNKNOWN_REFRESH_TOKEN)).thenReturn(null);

    String result = redisService.findUserIdByRefreshToken(UNKNOWN_REFRESH_TOKEN);

    assertThat(result, is(nullValue()));
  }
}