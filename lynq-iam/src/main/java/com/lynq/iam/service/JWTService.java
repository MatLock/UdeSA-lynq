package com.lynq.iam.service;

import com.lynq.iam.aspect.AuditLog;
import com.lynq.iam.model.UserEntity;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

@Service
@Log4j2
public class JWTService {

  private static final String USERNAME_CLAIM = "username";
  private static final String EMAIL_CLAIM = "email";

  private final String secret;
  private final long accessTokenExpirationMinutes;

  public JWTService(
      @Value("${lynq.security.jwt.secret}") String secret,
      @Value("${lynq.security.jwt.access-token-expiration-minutes}") long accessTokenExpirationMinutes) {
    this.secret = secret;
    this.accessTokenExpirationMinutes = accessTokenExpirationMinutes;
  }

  private Key getSigningKey() {
    return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
  }


  @AuditLog
  public String generateAccessToken(UserEntity user) {
    Instant now = Instant.now();
    return Jwts.builder()
      .subject(user.getId())
      .claim(USERNAME_CLAIM, user.getUsername())
      .claim(EMAIL_CLAIM, user.getEmail())
      .issuedAt(Date.from(now))
      .expiration(Date.from(now.plus(accessTokenExpirationMinutes, ChronoUnit.MINUTES)))
      .signWith(getSigningKey())
      .compact();
  }

  @AuditLog
  public String extractUserId(String token) {
    return parseClaims(token).getSubject();
  }

  @AuditLog
  public String extractUsername(String token) {
    return parseClaims(token).get(USERNAME_CLAIM, String.class);
  }

  @AuditLog
  public String extractEmail(String token) {
    return parseClaims(token).get(EMAIL_CLAIM, String.class);
  }

  private Claims parseClaims(String token) {
    return Jwts.parser()
        .verifyWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)))
        .build()
        .parseSignedClaims(token)
        .getPayload();
  }

  @AuditLog
  public boolean isAccessTokenValid(String token) {
    try {
      Jwts.parser()
          .verifyWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)))
          .build()
          .parseSignedClaims(token);
      return true;
    } catch (Exception e) {
      log.error("message= Access token validation failed, token={}", token, e);
      return false;
    }
  }

}
