package com.lynq.iam.controller.request;

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

class UserUpdatePasswordRequestTest {

  private static final String VALID_NEW_PASSWORD = "N3wStr0ngPass!";
  private static final String BLANK_VALUE = "";
  private static final String SHORT_PASSWORD = "1234567";

  private static final String NEW_PASSWORD_FIELD = "newPassword";

  private Validator validator;

  @BeforeEach
  void setUp() {
    try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
      validator = factory.getValidator();
    }
  }

  @Test
  void noArgsConstructorProducesNullNewPassword() {
    UserUpdatePasswordRequest request = new UserUpdatePasswordRequest();

    assertThat(request.getNewPassword(), is(nullValue()));
  }

  @Test
  void setterUpdatesNewPasswordField() {
    UserUpdatePasswordRequest request = new UserUpdatePasswordRequest();

    request.setNewPassword(VALID_NEW_PASSWORD);

    assertThat(request.getNewPassword(), is(VALID_NEW_PASSWORD));
  }

  @Test
  void validRequestHasNoViolations() {
    UserUpdatePasswordRequest request = new UserUpdatePasswordRequest();
    request.setNewPassword(VALID_NEW_PASSWORD);

    Set<ConstraintViolation<UserUpdatePasswordRequest>> violations = validator.validate(request);

    assertThat(violations, is(empty()));
  }

  @Test
  void blankNewPasswordProducesViolationOnNewPasswordField() {
    UserUpdatePasswordRequest request = new UserUpdatePasswordRequest();
    request.setNewPassword(BLANK_VALUE);

    Set<ConstraintViolation<UserUpdatePasswordRequest>> violations = validator.validate(request);

    assertThat(violationPaths(violations), hasItem(NEW_PASSWORD_FIELD));
  }

  @Test
  void nullNewPasswordProducesViolationOnNewPasswordField() {
    UserUpdatePasswordRequest request = new UserUpdatePasswordRequest();

    Set<ConstraintViolation<UserUpdatePasswordRequest>> violations = validator.validate(request);

    assertThat(violationPaths(violations), hasItem(NEW_PASSWORD_FIELD));
  }

  @Test
  void shortNewPasswordProducesViolationOnNewPasswordField() {
    UserUpdatePasswordRequest request = new UserUpdatePasswordRequest();
    request.setNewPassword(SHORT_PASSWORD);

    Set<ConstraintViolation<UserUpdatePasswordRequest>> violations = validator.validate(request);

    assertThat(violationPaths(violations), hasItem(NEW_PASSWORD_FIELD));
  }

  private static Set<String> violationPaths(Set<? extends ConstraintViolation<?>> violations) {
    return violations.stream()
        .map(v -> v.getPropertyPath().toString())
        .collect(Collectors.toSet());
  }
}