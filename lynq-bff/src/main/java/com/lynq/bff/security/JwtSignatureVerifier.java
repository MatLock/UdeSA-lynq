package com.lynq.bff.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import javax.crypto.SecretKey;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Log4j2
public class JwtSignatureVerifier {

  private final SecretKey signingKey;

  public JwtSignatureVerifier(@Value("${lynq.security.jwt.secret}") String secret) {
    this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
  }

  public Optional<String> verifiedSubject(String token) {
    if (token == null || token.isBlank()) {
      return Optional.empty();
    }
    try {
      String subject = Jwts.parser()
          .verifyWith(signingKey)
          .build()
          .parseSignedClaims(token)
          .getPayload()
          .getSubject();

      if (subject == null || subject.isBlank()) {
        log.warn("message= Access token verified but carries no subject claim");
        return Optional.empty();
      }
      return Optional.of(subject);
    } catch (Exception e) {
      log.warn("message= Access token signature verification failed, cause={}", e.getMessage());
      return Optional.empty();
    }
  }
}
