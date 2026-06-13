package com.lynq.iam.unit.controller.request;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

class UsernameLoginTest {

  private static final String VALID_USERNAME = "johndoe";
  private static final String VALID_PASSWORD = "P@ssw0rd123";
  private static final String BLANK_VALUE = "";
  private static final String SHORT_USERNAME = "jo";
  private static final String LONG_USERNAME = "a".repeat(21);
  private static final String SHORT_PASSWORD = "1234567";

  private static final String USERNAME_FIELD = "username";
  private static final String PASSWORD_FIELD = "password";

  private Validator validator;

  @BeforeEach
  void setUp() {
    try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
      validator = factory.getValidator();
    }
  }

  @Test
  void allArgsConstructorAssignsAllFields() {
    UsernameLogin login = new UsernameLogin(VALID_USERNAME, VALID_PASSWORD);

    assertThat(login.getUsername(), is(VALID_USERNAME));
    assertThat(login.getPassword(), is(VALID_PASSWORD));
  }

  @Test
  void noArgsConstructorProducesNullFields() {
    UsernameLogin login = new UsernameLogin();

    assertThat(login.getUsername(), is(nullValue()));
    assertThat(login.getPassword(), is(nullValue()));
  }

  @Test
  void settersUpdateFieldValues() {
    UsernameLogin login = new UsernameLogin();

    login.setUsername(VALID_USERNAME);
    login.setPassword(VALID_PASSWORD);

    assertThat(login.getUsername(), is(VALID_USERNAME));
    assertThat(login.getPassword(), is(VALID_PASSWORD));
  }

  @Test
  void validLoginHasNoViolations() {
    UsernameLogin login = new UsernameLogin(VALID_USERNAME, VALID_PASSWORD);

    Set<ConstraintViolation<UsernameLogin>> violations = validator.validate(login);

    assertThat(violations, is(empty()));
  }

  @Test
  void blankUsernameProducesViolationOnUsernameField() {
    UsernameLogin login = new UsernameLogin(BLANK_VALUE, VALID_PASSWORD);

    Set<ConstraintViolation<UsernameLogin>> violations = validator.validate(login);

    assertThat(violationPaths(violations), hasItem(USERNAME_FIELD));
  }

  @Test
  void shortUsernameProducesViolationOnUsernameField() {
    UsernameLogin login = new UsernameLogin(SHORT_USERNAME, VALID_PASSWORD);

    Set<ConstraintViolation<UsernameLogin>> violations = validator.validate(login);

    assertThat(violationPaths(violations), hasItem(USERNAME_FIELD));
  }

  @Test
  void longUsernameProducesViolationOnUsernameField() {
    UsernameLogin login = new UsernameLogin(LONG_USERNAME, VALID_PASSWORD);

    Set<ConstraintViolation<UsernameLogin>> violations = validator.validate(login);

    assertThat(violationPaths(violations), hasItem(USERNAME_FIELD));
  }

  @Test
  void blankPasswordProducesViolationOnPasswordField() {
    UsernameLogin login = new UsernameLogin(VALID_USERNAME, BLANK_VALUE);

    Set<ConstraintViolation<UsernameLogin>> violations = validator.validate(login);

    assertThat(violationPaths(violations), hasItem(PASSWORD_FIELD));
  }

  @Test
  void shortPasswordProducesViolationOnPasswordField() {
    UsernameLogin login = new UsernameLogin(VALID_USERNAME, SHORT_PASSWORD);

    Set<ConstraintViolation<UsernameLogin>> violations = validator.validate(login);

    assertThat(violationPaths(violations), hasItem(PASSWORD_FIELD));
  }

  private static Set<String> violationPaths(Set<? extends ConstraintViolation<?>> violations) {
    return violations.stream()
        .map(v -> v.getPropertyPath().toString())
        .collect(Collectors.toSet());
  }
}