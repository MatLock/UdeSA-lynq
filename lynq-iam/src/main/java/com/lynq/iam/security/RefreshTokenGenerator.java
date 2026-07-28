package com.lynq.iam.security;

import com.lynq.iam.aspect.AuditLog;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.Base64;

@Service
public class RefreshTokenGenerator {

  private static final int TOKEN_BYTES = 64;

  private final SecureRandom secureRandom;

  public RefreshTokenGenerator(SecureRandom secureRandom) {
    this.secureRandom = secureRandom;
  }

  @AuditLog
  public String generate() {
    byte[] bytes = new byte[TOKEN_BYTES];
    secureRandom.nextBytes(bytes);
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(bytes);
  }
}
