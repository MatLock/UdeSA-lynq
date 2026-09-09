package com.lynq.iam.controller.response;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

class UserInfoRestResponseTest {

  private static final String SAMPLE_ID = "550e8400-e29b-41d4-a716-446655440000";
  private static final String SAMPLE_USERNAME = "johndoe";
  private static final String SAMPLE_EMAIL = "johndoe@example.com";
  private static final List<String> SAMPLE_ROLES = List.of("R_CANDIDATE");

  @Test
  void allArgsConstructorAssignsAllFields() {
    UserInfoRestResponse response =
        new UserInfoRestResponse(SAMPLE_ID, SAMPLE_USERNAME, SAMPLE_EMAIL, SAMPLE_ROLES);

    assertThat(response.getId(), is(SAMPLE_ID));
    assertThat(response.getUsername(), is(SAMPLE_USERNAME));
    assertThat(response.getEmail(), is(SAMPLE_EMAIL));
    assertThat(response.getRoles(), is(SAMPLE_ROLES));
  }

  @Test
  void noArgsConstructorProducesNullFields() {
    UserInfoRestResponse response = new UserInfoRestResponse();

    assertThat(response.getId(), is(nullValue()));
    assertThat(response.getUsername(), is(nullValue()));
    assertThat(response.getEmail(), is(nullValue()));
    assertThat(response.getRoles(), is(nullValue()));
  }

  @Test
  void builderProducesEquivalentInstance() {
    UserInfoRestResponse built = UserInfoRestResponse.builder()
        .id(SAMPLE_ID)
        .username(SAMPLE_USERNAME)
        .email(SAMPLE_EMAIL)
        .roles(SAMPLE_ROLES)
        .build();

    assertThat(built, is(notNullValue()));
    assertThat(built.getId(), is(SAMPLE_ID));
    assertThat(built.getUsername(), is(SAMPLE_USERNAME));
    assertThat(built.getEmail(), is(SAMPLE_EMAIL));
    assertThat(built.getRoles(), is(SAMPLE_ROLES));
  }

  @Test
  void settersUpdateFieldValues() {
    UserInfoRestResponse response = new UserInfoRestResponse();

    response.setId(SAMPLE_ID);
    response.setUsername(SAMPLE_USERNAME);
    response.setEmail(SAMPLE_EMAIL);
    response.setRoles(SAMPLE_ROLES);

    assertThat(response.getId(), is(SAMPLE_ID));
    assertThat(response.getUsername(), is(SAMPLE_USERNAME));
    assertThat(response.getEmail(), is(SAMPLE_EMAIL));
    assertThat(response.getRoles(), is(SAMPLE_ROLES));
  }
}