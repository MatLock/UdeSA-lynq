package com.lynq.iam.unit.model;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.Month;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

class UserEntityTest {

  private static final String SAMPLE_ID = "550e8400-e29b-41d4-a716-446655440000";
  private static final String SAMPLE_EMAIL = "johndoe@example.com";
  private static final String SAMPLE_USERNAME = "johndoe";
  private static final String SAMPLE_PASSWORD_HASH = "$2a$10$abcdefghijklmnopqrstuv";
  private static final int CREATION_YEAR = 2026;
  private static final Month CREATION_MONTH = Month.JUNE;
  private static final int CREATION_DAY = 13;
  private static final int CREATION_HOUR = 14;
  private static final int CREATION_MINUTE = 30;
  private static final LocalDateTime SAMPLE_CREATION_DATE =
      LocalDateTime.of(CREATION_YEAR, CREATION_MONTH, CREATION_DAY, CREATION_HOUR, CREATION_MINUTE);

  @Test
  void allArgsConstructorAssignsAllFields() {
    UserEntity user = new UserEntity(
        SAMPLE_ID, SAMPLE_EMAIL, SAMPLE_USERNAME, SAMPLE_PASSWORD_HASH, SAMPLE_CREATION_DATE);

    assertThat(user.getId(), is(SAMPLE_ID));
    assertThat(user.getEmail(), is(SAMPLE_EMAIL));
    assertThat(user.getUsername(), is(SAMPLE_USERNAME));
    assertThat(user.getPassword(), is(SAMPLE_PASSWORD_HASH));
    assertThat(user.getCreationDate(), is(SAMPLE_CREATION_DATE));
  }

  @Test
  void noArgsConstructorProducesNullFields() {
    UserEntity user = new UserEntity();

    assertThat(user.getId(), is(nullValue()));
    assertThat(user.getEmail(), is(nullValue()));
    assertThat(user.getUsername(), is(nullValue()));
    assertThat(user.getPassword(), is(nullValue()));
    assertThat(user.getCreationDate(), is(nullValue()));
  }

  @Test
  void builderProducesEquivalentInstance() {
    UserEntity built = UserEntity.builder()
        .id(SAMPLE_ID)
        .email(SAMPLE_EMAIL)
        .username(SAMPLE_USERNAME)
        .password(SAMPLE_PASSWORD_HASH)
        .creationDate(SAMPLE_CREATION_DATE)
        .build();

    assertThat(built, is(notNullValue()));
    assertThat(built.getId(), is(SAMPLE_ID));
    assertThat(built.getEmail(), is(SAMPLE_EMAIL));
    assertThat(built.getUsername(), is(SAMPLE_USERNAME));
    assertThat(built.getPassword(), is(SAMPLE_PASSWORD_HASH));
    assertThat(built.getCreationDate(), is(SAMPLE_CREATION_DATE));
  }

  @Test
  void settersUpdateFieldValues() {
    UserEntity user = new UserEntity();

    user.setId(SAMPLE_ID);
    user.setEmail(SAMPLE_EMAIL);
    user.setUsername(SAMPLE_USERNAME);
    user.setPassword(SAMPLE_PASSWORD_HASH);
    user.setCreationDate(SAMPLE_CREATION_DATE);

    assertThat(user.getId(), is(SAMPLE_ID));
    assertThat(user.getEmail(), is(SAMPLE_EMAIL));
    assertThat(user.getUsername(), is(SAMPLE_USERNAME));
    assertThat(user.getPassword(), is(SAMPLE_PASSWORD_HASH));
    assertThat(user.getCreationDate(), is(SAMPLE_CREATION_DATE));
  }
}