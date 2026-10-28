package com.lynq.bff.security;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.when;

import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseCookie;

@ExtendWith(MockitoExtension.class)
class RefreshTokenCookieTest {

  private static final String CONTEXT_PATH = "/lynq-bff";
  private static final String EXPECTED_PATH = "/lynq-bff/auth";
  private static final String REFRESH_TOKEN = "8f14e45f-ceea-467a-9ae4-9b3f4a1c2d3e";
  private static final Duration MAX_AGE = Duration.ofDays(30);
  private static final Duration SESSION_COOKIE_MAX_AGE = Duration.ofSeconds(-1);

  @Mock
  private RefreshTokenCookieProperties properties;

  private RefreshTokenCookie refreshTokenCookie;

  @BeforeEach
  void setUp() {
    refreshTokenCookie = new RefreshTokenCookie(properties, CONTEXT_PATH);
  }

  @Test
  void issuesAHttpOnlyStrictHostOnlyCookieScopedToTheAuthRoutes() {
    when(properties.secure()).thenReturn(true);

    ResponseCookie cookie = refreshTokenCookie.issue(REFRESH_TOKEN, false);

    assertThat(cookie.getName(), is(RefreshTokenCookie.NAME));
    assertThat(cookie.getValue(), is(REFRESH_TOKEN));
    assertThat(cookie.isHttpOnly(), is(true));
    assertThat(cookie.isSecure(), is(true));
    assertThat(cookie.getSameSite(), is("Strict"));
    assertThat(cookie.getPath(), is(EXPECTED_PATH));
    assertThat(cookie.getDomain(), is(nullValue()));
  }

  @Test
  void issuesASessionCookieWhenTheLoginIsNotRemembered() {
    when(properties.secure()).thenReturn(true);

    ResponseCookie cookie = refreshTokenCookie.issue(REFRESH_TOKEN, false);

    assertThat(cookie.getMaxAge(), is(SESSION_COOKIE_MAX_AGE));
  }

  @Test
  void issuesAPersistentCookieThatLivesAsLongAsTheTokenWhenTheLoginIsRemembered() {
    when(properties.secure()).thenReturn(true);
    when(properties.maxAge()).thenReturn(MAX_AGE);

    ResponseCookie cookie = refreshTokenCookie.issue(REFRESH_TOKEN, true);

    assertThat(cookie.getMaxAge(), is(MAX_AGE));
  }

  @Test
  void dropsTheSecureFlagWhenConfiguredForPlainHttp() {
    when(properties.secure()).thenReturn(false);

    ResponseCookie cookie = refreshTokenCookie.issue(REFRESH_TOKEN, false);

    assertThat(cookie.isSecure(), is(false));
  }

  @Test
  void clearsTheCookieWithAnEmptyValueAndZeroMaxAgeOnTheSamePath() {
    when(properties.secure()).thenReturn(true);

    ResponseCookie cookie = refreshTokenCookie.clear();

    assertThat(cookie.getName(), is(RefreshTokenCookie.NAME));
    assertThat(cookie.getValue(), is(""));
    assertThat(cookie.getMaxAge(), is(Duration.ZERO));
    assertThat(cookie.getPath(), is(EXPECTED_PATH));
    assertThat(cookie.isHttpOnly(), is(true));
  }
}
