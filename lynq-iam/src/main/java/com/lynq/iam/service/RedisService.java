package com.lynq.iam.service;

import com.lynq.iam.aspect.AuditLog;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class RedisService {

  private final RedisTemplate<String, String> redisTemplate;
  private final long refreshTokenExpirationDays;

  public RedisService(
      RedisTemplate<String, String> redisTemplate,
      @Value("${lynq.security.jwt.refresh-token-expiration-days}") long refreshTokenExpirationDays) {
    this.redisTemplate = redisTemplate;
    this.refreshTokenExpirationDays = refreshTokenExpirationDays;
  }


  @AuditLog
  public void saveRefreshTokenForUser(String userId, String refreshToken) {
    redisTemplate.opsForValue().set(
        "refresh:" + refreshToken,
        userId,
        Duration.ofDays(refreshTokenExpirationDays)
    );
  }

  public String findUserIdByRefreshToken(String refreshToken) {
    return redisTemplate.opsForValue().get("refresh:" + refreshToken);
  }
}
