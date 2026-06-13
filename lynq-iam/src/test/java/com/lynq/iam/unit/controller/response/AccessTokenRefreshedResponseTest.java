package com.lynq.iam.unit.controller.response;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

class AccessTokenRefreshedResponseTest {

  private static final String SAMPLE_ACCESS_TOKEN = "eyJhbGciOiJIUzI1NiJ9.sample.token";

  @Test
  void noArgsConstructorProducesNullAccessToken() {
    AccessTokenRefreshedResponse response = new AccessTokenRefreshedResponse();

    assertThat(response.getAccessToken(), is(nullValue()));
  }

  @Test
  void setterUpdatesAccessTokenField() {
    AccessTokenRefreshedResponse response = new AccessTokenRefreshedResponse();

    response.setAccessToken(SAMPLE_ACCESS_TOKEN);

    assertThat(response.getAccessToken(), is(SAMPLE_ACCESS_TOKEN));
  }
}