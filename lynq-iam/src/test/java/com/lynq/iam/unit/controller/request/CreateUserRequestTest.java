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
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

class CreateUserRequestTest {

  private static final String VALID_USERNAME = "johndoe";
  private static final String VALID_PASSWORD = "P@ssw0rd123";
  private static final String VALID_EMAIL = "johndoe@example.com";
  private static final String BLANK_VALUE = "";
  private static final String SHORT_USERNAME = "jo";
  private static final String LONG_USERNAME = "a".repeat(21);
  private static final String SHORT_PASSWORD = "1234567";
  private static final String INVALID_EMAIL = "not-an-email";
  private static final String LONG_EMAIL = "a".repeat(95) + "@a.com";

  private static final String USERNAME_FIELD = "username";
  private static final String PASSWORD_FIELD = "password";
  private static final String EMAIL_FIELD = "email";

  private Validator validator;

  @BeforeEach
  void setUp() {
    try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
      validator = factory.getValidator();
    }
  }

  @Test
  void allArgsConstructorAssignsAllFields() {
    CreateUserRequest request = new CreateUserRequest(VALID_USERNAME, VALID_PASSWORD, VALID_EMAIL);

    assertThat(request.getUsername(), is(VALID_USERNAME));
    assertThat(request.getPassword(), is(VALID_PASSWORD));
    assertThat(request.getEmail(), is(VALID_EMAIL));
  }

  @Test
  void noArgsConstructorProducesNullFields() {
    CreateUserRequest request = new CreateUserRequest();

    assertThat(request.getUsername(), is(nullValue()));
    assertThat(request.getPassword(), is(nullValue()));
    assertThat(request.getEmail(), is(nullValue()));
  }

  @Test
  void builderProducesEquivalentInstance() {
    CreateUserRequest built = CreateUserRequest.builder()
        .username(VALID_USERNAME)
        .password(VALID_PASSWORD)
        .email(VALID_EMAIL)
        .build();

    assertThat(built, is(notNullValue()));
    assertThat(built.getUsername(), is(VALID_USERNAME));
    assertThat(built.getPassword(), is(VALID_PASSWORD));
    assertThat(built.getEmail(), is(VALID_EMAIL));
  }

  @Test
  void settersUpdateFieldValues() {
    CreateUserRequest request = new CreateUserRequest();

    request.setUsername(VALID_USERNAME);
    request.setPassword(VALID_PASSWORD);
    request.setEmail(VALID_EMAIL);

    assertThat(request.getUsername(), is(VALID_USERNAME));
    assertThat(request.getPassword(), is(VALID_PASSWORD));
    assertThat(request.getEmail(), is(VALID_EMAIL));
  }

  @Test
  void validRequestHasNoViolations() {
    CreateUserRequest request = new CreateUserRequest(VALID_USERNAME, VALID_PASSWORD, VALID_EMAIL);

    Set<ConstraintViolation<CreateUserRequest>> violations = validator.validate(request);

    assertThat(violations, is(empty()));
  }

  @Test
  void blankUsernameProducesViolationOnUsernameField() {
    CreateUserRequest request = new CreateUserRequest(BLANK_VALUE, VALID_PASSWORD, VALID_EMAIL);

    Set<ConstraintViolation<CreateUserRequest>> violations = validator.validate(request);

    assertThat(violationPaths(violations), hasItem(USERNAME_FIELD));
  }

  @Test
  void shortUsernameProducesViolationOnUsernameField() {
    CreateUserRequest request = new CreateUserRequest(SHORT_USERNAME, VALID_PASSWORD, VALID_EMAIL);

    Set<ConstraintViolation<CreateUserRequest>> violations = validator.validate(request);

    assertThat(violationPaths(violations), hasItem(USERNAME_FIELD));
  }

  @Test
  void longUsernameProducesViolationOnUsernameField() {
    CreateUserRequest request = new CreateUserRequest(LONG_USERNAME, VALID_PASSWORD, VALID_EMAIL);

    Set<ConstraintViolation<CreateUserRequest>> violations = validator.validate(request);

    assertThat(violationPaths(violations), hasItem(USERNAME_FIELD));
  }

  @Test
  void blankPasswordProducesViolationOnPasswordField() {
    CreateUserRequest request = new CreateUserRequest(VALID_USERNAME, BLANK_VALUE, VALID_EMAIL);

    Set<ConstraintViolation<CreateUserRequest>> violations = validator.validate(request);

    assertThat(violationPaths(violations), hasItem(PASSWORD_FIELD));
  }

  @Test
  void shortPasswordProducesViolationOnPasswordField() {
    CreateUserRequest request = new CreateUserRequest(VALID_USERNAME, SHORT_PASSWORD, VALID_EMAIL);

    Set<ConstraintViolation<CreateUserRequest>> violations = validator.validate(request);

    assertThat(violationPaths(violations), hasItem(PASSWORD_FIELD));
  }

  @Test
  void blankEmailProducesViolationOnEmailField() {
    CreateUserRequest request = new CreateUserRequest(VALID_USERNAME, VALID_PASSWORD, BLANK_VALUE);

    Set<ConstraintViolation<CreateUserRequest>> violations = validator.validate(request);

    assertThat(violationPaths(violations), hasItem(EMAIL_FIELD));
  }

  @Test
  void invalidEmailFormatProducesViolationOnEmailField() {
    CreateUserRequest request = new CreateUserRequest(VALID_USERNAME, VALID_PASSWORD, INVALID_EMAIL);

    Set<ConstraintViolation<CreateUserRequest>> violations = validator.validate(request);

    assertThat(violationPaths(violations), hasItem(EMAIL_FIELD));
  }

  @Test
  void longEmailProducesViolationOnEmailField() {
    CreateUserRequest request = new CreateUserRequest(VALID_USERNAME, VALID_PASSWORD, LONG_EMAIL);

    Set<ConstraintViolation<CreateUserRequest>> violations = validator.validate(request);

    assertThat(violationPaths(violations), hasItem(EMAIL_FIELD));
  }

  private static Set<String> violationPaths(Set<? extends ConstraintViolation<?>> violations) {
    return violations.stream()
        .map(v -> v.getPropertyPath().toString())
        .collect(Collectors.toSet());
  }
}