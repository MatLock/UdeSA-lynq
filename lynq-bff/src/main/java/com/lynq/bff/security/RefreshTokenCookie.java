package com.lynq.bff.security;

import java.time.Duration;
import org.springframework.http.ResponseCookie;

public class RefreshTokenCookie {

  public static final String NAME = "lynq_refresh";

  private static final String AUTH_PATH = "/auth";
  private static final String SAME_SITE = "Strict";

  private final RefreshTokenCookieProperties properties;
  private final String path;

  public RefreshTokenCookie(RefreshTokenCookieProperties properties, String contextPath) {
    this.properties = properties;
    this.path = contextPath + AUTH_PATH;
  }

  public ResponseCookie issue(String refreshToken, boolean remember) {
    ResponseCookie.ResponseCookieBuilder cookie = base(refreshToken);
    if (remember) {
      cookie.maxAge(properties.maxAge());
    }
    return cookie.build();
  }

  public ResponseCookie clear() {
    return base("").maxAge(Duration.ZERO).build();
  }

  private ResponseCookie.ResponseCookieBuilder base(String value) {
    return ResponseCookie.from(NAME, value)
        .httpOnly(true)
        .secure(properties.secure())
        .sameSite(SAME_SITE)
        .path(path);
  }
}
