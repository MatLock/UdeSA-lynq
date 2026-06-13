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

class EmailUserLoginTest {

  private static final String VALID_EMAIL = "johndoe@example.com";
  private static final String VALID_PASSWORD = "P@ssw0rd123";
  private static final String BLANK_VALUE = "";
  private static final String INVALID_EMAIL = "not-an-email";
  private static final String LONG_EMAIL = "a".repeat(95) + "@a.com";
  private static final String SHORT_PASSWORD = "1234567";

  private static final String EMAIL_FIELD = "email";
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
    EmailUserLogin login = new EmailUserLogin(VALID_EMAIL, VALID_PASSWORD);

    assertThat(login.getEmail(), is(VALID_EMAIL));
    assertThat(login.getPassword(), is(VALID_PASSWORD));
  }

  @Test
  void noArgsConstructorProducesNullFields() {
    EmailUserLogin login = new EmailUserLogin();

    assertThat(login.getEmail(), is(nullValue()));
    assertThat(login.getPassword(), is(nullValue()));
  }

  @Test
  void settersUpdateFieldValues() {
    EmailUserLogin login = new EmailUserLogin();

    login.setEmail(VALID_EMAIL);
    login.setPassword(VALID_PASSWORD);

    assertThat(login.getEmail(), is(VALID_EMAIL));
    assertThat(login.getPassword(), is(VALID_PASSWORD));
  }

  @Test
  void validLoginHasNoViolations() {
    EmailUserLogin login = new EmailUserLogin(VALID_EMAIL, VALID_PASSWORD);

    Set<ConstraintViolation<EmailUserLogin>> violations = validator.validate(login);

    assertThat(violations, is(empty()));
  }

  @Test
  void blankEmailProducesViolationOnEmailField() {
    EmailUserLogin login = new EmailUserLogin(BLANK_VALUE, VALID_PASSWORD);

    Set<ConstraintViolation<EmailUserLogin>> violations = validator.validate(login);

    assertThat(violationPaths(violations), hasItem(EMAIL_FIELD));
  }

  @Test
  void invalidEmailFormatProducesViolationOnEmailField() {
    EmailUserLogin login = new EmailUserLogin(INVALID_EMAIL, VALID_PASSWORD);

    Set<ConstraintViolation<EmailUserLogin>> violations = validator.validate(login);

    assertThat(violationPaths(violations), hasItem(EMAIL_FIELD));
  }

  @Test
  void longEmailProducesViolationOnEmailField() {
    EmailUserLogin login = new EmailUserLogin(LONG_EMAIL, VALID_PASSWORD);

    Set<ConstraintViolation<EmailUserLogin>> violations = validator.validate(login);

    assertThat(violationPaths(violations), hasItem(EMAIL_FIELD));
  }

  @Test
  void blankPasswordProducesViolationOnPasswordField() {
    EmailUserLogin login = new EmailUserLogin(VALID_EMAIL, BLANK_VALUE);

    Set<ConstraintViolation<EmailUserLogin>> violations = validator.validate(login);

    assertThat(violationPaths(violations), hasItem(PASSWORD_FIELD));
  }

  @Test
  void shortPasswordProducesViolationOnPasswordField() {
    EmailUserLogin login = new EmailUserLogin(VALID_EMAIL, SHORT_PASSWORD);

    Set<ConstraintViolation<EmailUserLogin>> violations = validator.validate(login);

    assertThat(violationPaths(violations), hasItem(PASSWORD_FIELD));
  }

  private static Set<String> violationPaths(Set<? extends ConstraintViolation<?>> violations) {
    return violations.stream()
        .map(v -> v.getPropertyPath().toString())
        .collect(Collectors.toSet());
  }
}