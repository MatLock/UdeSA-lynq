package com.lynq.iam.controller.response;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

class UserRestResponseTest {

  private static final String SAMPLE_ID = "550e8400-e29b-41d4-a716-446655440000";
  private static final String SAMPLE_USERNAME = "johndoe";
  private static final String SAMPLE_EMAIL = "johndoe@example.com";
  private static final OffsetDateTime SAMPLE_CREATION_DATE =
      OffsetDateTime.of(2026, 6, 2, 19, 30, 0, 0, ZoneOffset.UTC);
  private static final String SAMPLE_REFRESH_TOKEN = "eyJhbGciOiJIUzI1NiJ9.refresh.token";
  private static final String SAMPLE_ACCESS_TOKEN = "eyJhbGciOiJIUzI1NiJ9.access.token";

  @Test
  void allArgsConstructorAssignsAllFields() {
    UserRestResponse response = new UserRestResponse(
        SAMPLE_ID, SAMPLE_USERNAME, SAMPLE_EMAIL, SAMPLE_CREATION_DATE,
        SAMPLE_REFRESH_TOKEN, SAMPLE_ACCESS_TOKEN);

    assertThat(response.getId(), is(SAMPLE_ID));
    assertThat(response.getUsername(), is(SAMPLE_USERNAME));
    assertThat(response.getEmail(), is(SAMPLE_EMAIL));
    assertThat(response.getCreationDate(), is(SAMPLE_CREATION_DATE));
    assertThat(response.getRefreshToken(), is(SAMPLE_REFRESH_TOKEN));
    assertThat(response.getAccessToken(), is(SAMPLE_ACCESS_TOKEN));
  }

  @Test
  void noArgsConstructorProducesNullFields() {
    UserRestResponse response = new UserRestResponse();

    assertThat(response.getId(), is(nullValue()));
    assertThat(response.getUsername(), is(nullValue()));
    assertThat(response.getEmail(), is(nullValue()));
    assertThat(response.getCreationDate(), is(nullValue()));
    assertThat(response.getRefreshToken(), is(nullValue()));
    assertThat(response.getAccessToken(), is(nullValue()));
  }

  @Test
  void builderProducesEquivalentInstance() {
    UserRestResponse built = UserRestResponse.builder()
        .id(SAMPLE_ID)
        .username(SAMPLE_USERNAME)
        .email(SAMPLE_EMAIL)
        .creationDate(SAMPLE_CREATION_DATE)
        .refreshToken(SAMPLE_REFRESH_TOKEN)
        .accessToken(SAMPLE_ACCESS_TOKEN)
        .build();

    assertThat(built, is(notNullValue()));
    assertThat(built.getId(), is(SAMPLE_ID));
    assertThat(built.getUsername(), is(SAMPLE_USERNAME));
    assertThat(built.getEmail(), is(SAMPLE_EMAIL));
    assertThat(built.getCreationDate(), is(SAMPLE_CREATION_DATE));
    assertThat(built.getRefreshToken(), is(SAMPLE_REFRESH_TOKEN));
    assertThat(built.getAccessToken(), is(SAMPLE_ACCESS_TOKEN));
  }

  @Test
  void settersUpdateFieldValues() {
    UserRestResponse response = new UserRestResponse();

    response.setId(SAMPLE_ID);
    response.setUsername(SAMPLE_USERNAME);
    response.setEmail(SAMPLE_EMAIL);
    response.setCreationDate(SAMPLE_CREATION_DATE);
    response.setRefreshToken(SAMPLE_REFRESH_TOKEN);
    response.setAccessToken(SAMPLE_ACCESS_TOKEN);

    assertThat(response.getId(), is(SAMPLE_ID));
    assertThat(response.getUsername(), is(SAMPLE_USERNAME));
    assertThat(response.getEmail(), is(SAMPLE_EMAIL));
    assertThat(response.getCreationDate(), is(SAMPLE_CREATION_DATE));
    assertThat(response.getRefreshToken(), is(SAMPLE_REFRESH_TOKEN));
    assertThat(response.getAccessToken(), is(SAMPLE_ACCESS_TOKEN));
  }
}