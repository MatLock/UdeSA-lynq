package com.lynq.iam.service;

import com.lynq.iam.controller.response.AccessTokenRefreshedResponse;
import com.lynq.iam.controller.response.CheckEmailResponse;
import com.lynq.iam.controller.response.CheckUsernameResponse;
import com.lynq.iam.controller.response.UserInfoRestResponse;
import com.lynq.iam.controller.response.UserRestResponse;
import com.lynq.iam.exceptions.ForbiddenException;
import com.lynq.iam.exceptions.InvalidPasswordException;
import com.lynq.iam.exceptions.UserNotFoundException;
import com.lynq.iam.model.UserEntity;
import com.lynq.iam.repository.UserRepository;
import com.lynq.iam.security.RefreshTokenGenerator;
import com.lynq.iam.service.AuthService;
import com.lynq.iam.service.JWTService;
import com.lynq.iam.service.RedisService;
import com.lynq.iam.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

  private static final String SAMPLE_USER_ID = "550e8400-e29b-41d4-a716-446655440000";
  private static final String SAMPLE_USERNAME = "johndoe";
  private static final String SAMPLE_EMAIL = "johndoe@example.com";
  private static final String SAMPLE_PASSWORD = "P@ssw0rd123";
  private static final String SAMPLE_ENCODED_PASSWORD = "$2a$10$encodedpasswordhash";
  private static final String SAMPLE_WRONG_PASSWORD = "wrong-password";
  private static final String SAMPLE_NEW_PASSWORD = "N3wStr0ngPass!";
  private static final String SAMPLE_ACCESS_TOKEN = "sample.access.token";
  private static final String SAMPLE_NEW_ACCESS_TOKEN = "new.access.token";
  private static final String SAMPLE_REFRESH_TOKEN = "sample-refresh-token-value";
  private static final String SAMPLE_INCOMING_REFRESH_TOKEN = "incoming-refresh-token";
  private static final String INVALID_REFRESH_TOKEN_MESSAGE = "Invalid refresh token";
  private static final String USER_NOT_FOUND_MESSAGE = "User not found";
  private static final String INVALID_USERNAME_OR_PASSWORD_MESSAGE = "Invalid username or password";
  private static final String INVALID_EMAIL_OR_PASSWORD_MESSAGE = "Invalid email or password";
  private static final boolean EXPECTED_VALID = true;
  private static final boolean EXPECTED_INVALID = false;
  private static final String BLANK_USERNAME = "   ";
  private static final String TOO_SHORT_USERNAME = "jo";
  private static final String TOO_LONG_USERNAME = "thisusernameiswaytoolongtobevalid";
  private static final String USERNAME_BLANK_REASON = "Username must not be blank";
  private static final String USERNAME_LENGTH_REASON = "Username must be between 3 and 20 characters";
  private static final String USERNAME_TAKEN_REASON = "Username is already taken";
  private static final String BLANK_EMAIL = "   ";
  private static final String TOO_LONG_EMAIL = "a".repeat(95) + "@e.com";
  private static final String MALFORMED_EMAIL = "not-an-email";
  private static final String EMAIL_BLANK_REASON = "Email must not be blank";
  private static final String EMAIL_LENGTH_REASON = "Email must not exceed 100 characters";
  private static final String EMAIL_FORMAT_REASON = "Email format is invalid";
  private static final String EMAIL_TAKEN_REASON = "Email is already taken";

  @Mock
  private UserService userService;

  @Mock
  private UserRepository userRepository;

  @Mock
  private BCryptPasswordEncoder passwordEncoder;

  @Mock
  private JWTService jwtService;

  @Mock
  private RefreshTokenGenerator refreshTokenGenerator;

  @Mock
  private RedisService redisService;

  private AuthService authService;
  private UserEntity sampleUser;

  @BeforeEach
  void setUp() {
    authService = new AuthService(userService, userRepository, passwordEncoder, jwtService,
        refreshTokenGenerator, redisService);
    sampleUser = UserEntity.builder()
        .id(SAMPLE_USER_ID)
        .username(SAMPLE_USERNAME)
        .email(SAMPLE_EMAIL)
        .password(SAMPLE_ENCODED_PASSWORD)
        .build();
  }

  @Test
  void registerUserReturnsResponseWithUserDataAndGeneratedTokens() {
    when(userService.createUser(SAMPLE_USERNAME, SAMPLE_PASSWORD, SAMPLE_EMAIL)).thenReturn(sampleUser);
    when(jwtService.generateAccessToken(sampleUser)).thenReturn(SAMPLE_ACCESS_TOKEN);
    when(refreshTokenGenerator.generate()).thenReturn(SAMPLE_REFRESH_TOKEN);

    UserRestResponse response = authService.registerUser(SAMPLE_USERNAME, SAMPLE_PASSWORD, SAMPLE_EMAIL);

    assertThat(response.getId(), is(SAMPLE_USER_ID));
    assertThat(response.getUsername(), is(SAMPLE_USERNAME));
    assertThat(response.getEmail(), is(SAMPLE_EMAIL));
    assertThat(response.getAccessToken(), is(SAMPLE_ACCESS_TOKEN));
    assertThat(response.getRefreshToken(), is(SAMPLE_REFRESH_TOKEN));
  }

  @Test
  void registerUserPersistsRefreshTokenForCreatedUser() {
    when(userService.createUser(SAMPLE_USERNAME, SAMPLE_PASSWORD, SAMPLE_EMAIL)).thenReturn(sampleUser);
    when(jwtService.generateAccessToken(sampleUser)).thenReturn(SAMPLE_ACCESS_TOKEN);
    when(refreshTokenGenerator.generate()).thenReturn(SAMPLE_REFRESH_TOKEN);

    authService.registerUser(SAMPLE_USERNAME, SAMPLE_PASSWORD, SAMPLE_EMAIL);

    verify(redisService).saveRefreshTokenForUser(SAMPLE_USER_ID, SAMPLE_REFRESH_TOKEN);
  }

  @Test
  void loginByUsernameReturnsResponseWithUserDataAndGeneratedTokensWhenCredentialsValid() {
    when(userRepository.findByUsername(SAMPLE_USERNAME)).thenReturn(Optional.of(sampleUser));
    when(passwordEncoder.matches(SAMPLE_PASSWORD, SAMPLE_ENCODED_PASSWORD)).thenReturn(true);
    when(jwtService.generateAccessToken(sampleUser)).thenReturn(SAMPLE_ACCESS_TOKEN);
    when(refreshTokenGenerator.generate()).thenReturn(SAMPLE_REFRESH_TOKEN);

    UserRestResponse response = authService.loginByUsername(SAMPLE_USERNAME, SAMPLE_PASSWORD);

    assertThat(response.getId(), is(SAMPLE_USER_ID));
    assertThat(response.getUsername(), is(SAMPLE_USERNAME));
    assertThat(response.getEmail(), is(SAMPLE_EMAIL));
    assertThat(response.getAccessToken(), is(SAMPLE_ACCESS_TOKEN));
    assertThat(response.getRefreshToken(), is(SAMPLE_REFRESH_TOKEN));
    verify(redisService).saveRefreshTokenForUser(SAMPLE_USER_ID, SAMPLE_REFRESH_TOKEN);
  }

  @Test
  void loginByUsernameThrowsUserNotFoundExceptionWhenUsernameDoesNotExist() {
    when(userRepository.findByUsername(SAMPLE_USERNAME)).thenReturn(Optional.empty());

    UserNotFoundException thrown = assertThrows(UserNotFoundException.class,
        () -> authService.loginByUsername(SAMPLE_USERNAME, SAMPLE_PASSWORD));

    assertThat(thrown.getMessage(), containsString(INVALID_USERNAME_OR_PASSWORD_MESSAGE));
    verify(jwtService, never()).generateAccessToken(any());
    verify(redisService, never()).saveRefreshTokenForUser(any(), any());
  }

  @Test
  void loginByUsernameThrowsInvalidPasswordExceptionWhenPasswordDoesNotMatch() {
    when(userRepository.findByUsername(SAMPLE_USERNAME)).thenReturn(Optional.of(sampleUser));
    when(passwordEncoder.matches(SAMPLE_WRONG_PASSWORD, SAMPLE_ENCODED_PASSWORD)).thenReturn(false);

    InvalidPasswordException thrown = assertThrows(InvalidPasswordException.class,
        () -> authService.loginByUsername(SAMPLE_USERNAME, SAMPLE_WRONG_PASSWORD));

    assertThat(thrown.getMessage(), containsString(INVALID_USERNAME_OR_PASSWORD_MESSAGE));
    verify(jwtService, never()).generateAccessToken(any());
    verify(redisService, never()).saveRefreshTokenForUser(any(), any());
  }

  @Test
  void loginByEmailReturnsResponseWithUserDataAndGeneratedTokensWhenCredentialsValid() {
    when(userRepository.findByEmail(SAMPLE_EMAIL)).thenReturn(Optional.of(sampleUser));
    when(passwordEncoder.matches(SAMPLE_PASSWORD, SAMPLE_ENCODED_PASSWORD)).thenReturn(true);
    when(jwtService.generateAccessToken(sampleUser)).thenReturn(SAMPLE_ACCESS_TOKEN);
    when(refreshTokenGenerator.generate()).thenReturn(SAMPLE_REFRESH_TOKEN);

    UserRestResponse response = authService.loginByEmail(SAMPLE_EMAIL, SAMPLE_PASSWORD);

    assertThat(response.getId(), is(SAMPLE_USER_ID));
    assertThat(response.getUsername(), is(SAMPLE_USERNAME));
    assertThat(response.getEmail(), is(SAMPLE_EMAIL));
    assertThat(response.getAccessToken(), is(SAMPLE_ACCESS_TOKEN));
    assertThat(response.getRefreshToken(), is(SAMPLE_REFRESH_TOKEN));
    verify(redisService).saveRefreshTokenForUser(SAMPLE_USER_ID, SAMPLE_REFRESH_TOKEN);
  }

  @Test
  void loginByEmailThrowsUserNotFoundExceptionWhenEmailDoesNotExist() {
    when(userRepository.findByEmail(SAMPLE_EMAIL)).thenReturn(Optional.empty());

    UserNotFoundException thrown = assertThrows(UserNotFoundException.class,
        () -> authService.loginByEmail(SAMPLE_EMAIL, SAMPLE_PASSWORD));

    assertThat(thrown.getMessage(), containsString(INVALID_EMAIL_OR_PASSWORD_MESSAGE));
    verify(jwtService, never()).generateAccessToken(any());
    verify(redisService, never()).saveRefreshTokenForUser(any(), any());
  }

  @Test
  void loginByEmailThrowsInvalidPasswordExceptionWhenPasswordDoesNotMatch() {
    when(userRepository.findByEmail(SAMPLE_EMAIL)).thenReturn(Optional.of(sampleUser));
    when(passwordEncoder.matches(SAMPLE_WRONG_PASSWORD, SAMPLE_ENCODED_PASSWORD)).thenReturn(false);

    InvalidPasswordException thrown = assertThrows(InvalidPasswordException.class,
        () -> authService.loginByEmail(SAMPLE_EMAIL, SAMPLE_WRONG_PASSWORD));

    assertThat(thrown.getMessage(), containsString(INVALID_EMAIL_OR_PASSWORD_MESSAGE));
    verify(jwtService, never()).generateAccessToken(any());
    verify(redisService, never()).saveRefreshTokenForUser(any(), any());
  }

  @Test
  void updatePasswordDelegatesToUserServiceAndReturnsResponseWithFreshTokens() {
    when(jwtService.extractUserId(SAMPLE_ACCESS_TOKEN)).thenReturn(SAMPLE_USER_ID);
    when(userRepository.findById(SAMPLE_USER_ID)).thenReturn(Optional.of(sampleUser));
    when(jwtService.generateAccessToken(sampleUser)).thenReturn(SAMPLE_NEW_ACCESS_TOKEN);
    when(refreshTokenGenerator.generate()).thenReturn(SAMPLE_REFRESH_TOKEN);

    UserRestResponse response = authService.updatePassword(SAMPLE_ACCESS_TOKEN, SAMPLE_NEW_PASSWORD);

    verify(userService).updatePassword(SAMPLE_USER_ID, SAMPLE_NEW_PASSWORD);
    verify(redisService).saveRefreshTokenForUser(SAMPLE_USER_ID, SAMPLE_REFRESH_TOKEN);
    assertThat(response.getId(), is(SAMPLE_USER_ID));
    assertThat(response.getUsername(), is(SAMPLE_USERNAME));
    assertThat(response.getEmail(), is(SAMPLE_EMAIL));
    assertThat(response.getAccessToken(), is(SAMPLE_NEW_ACCESS_TOKEN));
    assertThat(response.getRefreshToken(), is(SAMPLE_REFRESH_TOKEN));
  }

  @Test
  void updatePasswordThrowsUserNotFoundExceptionWhenUserMissingAfterUpdate() {
    when(jwtService.extractUserId(SAMPLE_ACCESS_TOKEN)).thenReturn(SAMPLE_USER_ID);
    when(userRepository.findById(SAMPLE_USER_ID)).thenReturn(Optional.empty());

    UserNotFoundException thrown = assertThrows(UserNotFoundException.class,
        () -> authService.updatePassword(SAMPLE_ACCESS_TOKEN, SAMPLE_NEW_PASSWORD));

    assertThat(thrown.getMessage(), containsString(USER_NOT_FOUND_MESSAGE));
    verify(jwtService, never()).generateAccessToken(any());
    verify(redisService, never()).saveRefreshTokenForUser(any(), any());
  }

  @Test
  void isAccessTokenValidReturnsTrueWhenJwtServiceReportsTokenValid() {
    when(jwtService.isAccessTokenValid(SAMPLE_ACCESS_TOKEN)).thenReturn(true);

    boolean result = authService.isAccessTokenValid(SAMPLE_ACCESS_TOKEN);

    assertThat(result, is(EXPECTED_VALID));
  }

  @Test
  void isAccessTokenValidReturnsFalseWhenJwtServiceReportsTokenInvalid() {
    when(jwtService.isAccessTokenValid(SAMPLE_ACCESS_TOKEN)).thenReturn(false);

    boolean result = authService.isAccessTokenValid(SAMPLE_ACCESS_TOKEN);

    assertThat(result, is(EXPECTED_INVALID));
  }

  @Test
  void checkUsernameReturnsValidWithoutReasonWhenFormatIsValidAndUsernameAvailable() {
    when(userRepository.existsByUsername(SAMPLE_USERNAME)).thenReturn(false);

    CheckUsernameResponse response = authService.checkUsername(SAMPLE_USERNAME);

    assertThat(response.isValid(), is(EXPECTED_VALID));
    assertThat(response.getReason(), is(nullValue()));
  }

  @Test
  void checkUsernameReturnsInvalidWithBlankReasonWhenUsernameIsBlank() {
    CheckUsernameResponse response = authService.checkUsername(BLANK_USERNAME);

    assertThat(response.isValid(), is(EXPECTED_INVALID));
    assertThat(response.getReason(), is(USERNAME_BLANK_REASON));
    verify(userRepository, never()).existsByUsername(any());
  }

  @Test
  void checkUsernameReturnsInvalidWithBlankReasonWhenUsernameIsNull() {
    CheckUsernameResponse response = authService.checkUsername(null);

    assertThat(response.isValid(), is(EXPECTED_INVALID));
    assertThat(response.getReason(), is(USERNAME_BLANK_REASON));
    verify(userRepository, never()).existsByUsername(any());
  }

  @Test
  void checkUsernameReturnsInvalidWithLengthReasonWhenUsernameTooShort() {
    CheckUsernameResponse response = authService.checkUsername(TOO_SHORT_USERNAME);

    assertThat(response.isValid(), is(EXPECTED_INVALID));
    assertThat(response.getReason(), is(USERNAME_LENGTH_REASON));
    verify(userRepository, never()).existsByUsername(any());
  }

  @Test
  void checkUsernameReturnsInvalidWithLengthReasonWhenUsernameTooLong() {
    CheckUsernameResponse response = authService.checkUsername(TOO_LONG_USERNAME);

    assertThat(response.isValid(), is(EXPECTED_INVALID));
    assertThat(response.getReason(), is(USERNAME_LENGTH_REASON));
    verify(userRepository, never()).existsByUsername(any());
  }

  @Test
  void checkUsernameReturnsInvalidWithTakenReasonWhenUsernameAlreadyExists() {
    when(userRepository.existsByUsername(SAMPLE_USERNAME)).thenReturn(true);

    CheckUsernameResponse response = authService.checkUsername(SAMPLE_USERNAME);

    assertThat(response.isValid(), is(EXPECTED_INVALID));
    assertThat(response.getReason(), is(USERNAME_TAKEN_REASON));
  }

  @Test
  void checkEmailReturnsValidWithoutReasonWhenFormatIsValidAndEmailAvailable() {
    when(userRepository.existsByEmail(SAMPLE_EMAIL)).thenReturn(false);

    CheckEmailResponse response = authService.checkEmail(SAMPLE_EMAIL);

    assertThat(response.isValid(), is(EXPECTED_VALID));
    assertThat(response.getReason(), is(nullValue()));
  }

  @Test
  void checkEmailReturnsInvalidWithBlankReasonWhenEmailIsBlank() {
    CheckEmailResponse response = authService.checkEmail(BLANK_EMAIL);

    assertThat(response.isValid(), is(EXPECTED_INVALID));
    assertThat(response.getReason(), is(EMAIL_BLANK_REASON));
    verify(userRepository, never()).existsByEmail(any());
  }

  @Test
  void checkEmailReturnsInvalidWithBlankReasonWhenEmailIsNull() {
    CheckEmailResponse response = authService.checkEmail(null);

    assertThat(response.isValid(), is(EXPECTED_INVALID));
    assertThat(response.getReason(), is(EMAIL_BLANK_REASON));
    verify(userRepository, never()).existsByEmail(any());
  }

  @Test
  void checkEmailReturnsInvalidWithLengthReasonWhenEmailTooLong() {
    CheckEmailResponse response = authService.checkEmail(TOO_LONG_EMAIL);

    assertThat(response.isValid(), is(EXPECTED_INVALID));
    assertThat(response.getReason(), is(EMAIL_LENGTH_REASON));
    verify(userRepository, never()).existsByEmail(any());
  }

  @Test
  void checkEmailReturnsInvalidWithFormatReasonWhenEmailMalformed() {
    CheckEmailResponse response = authService.checkEmail(MALFORMED_EMAIL);

    assertThat(response.isValid(), is(EXPECTED_INVALID));
    assertThat(response.getReason(), is(EMAIL_FORMAT_REASON));
    verify(userRepository, never()).existsByEmail(any());
  }

  @Test
  void checkEmailReturnsInvalidWithTakenReasonWhenEmailAlreadyExists() {
    when(userRepository.existsByEmail(SAMPLE_EMAIL)).thenReturn(true);

    CheckEmailResponse response = authService.checkEmail(SAMPLE_EMAIL);

    assertThat(response.isValid(), is(EXPECTED_INVALID));
    assertThat(response.getReason(), is(EMAIL_TAKEN_REASON));
  }

  @Test
  void obtainUserInfoFromTokenReturnsUserInfoBuiltFromTokenClaims() {
    when(jwtService.extractUserId(SAMPLE_ACCESS_TOKEN)).thenReturn(SAMPLE_USER_ID);
    when(jwtService.extractUsername(SAMPLE_ACCESS_TOKEN)).thenReturn(SAMPLE_USERNAME);
    when(jwtService.extractEmail(SAMPLE_ACCESS_TOKEN)).thenReturn(SAMPLE_EMAIL);

    UserInfoRestResponse response = authService.obtainUserInfoFromToken(SAMPLE_ACCESS_TOKEN);

    assertThat(response.getId(), is(SAMPLE_USER_ID));
    assertThat(response.getUsername(), is(SAMPLE_USERNAME));
    assertThat(response.getEmail(), is(SAMPLE_EMAIL));
  }

  @Test
  void generateNewAccessTokenReturnsResponseWithFreshAccessTokenWhenRefreshTokenValid() {
    when(redisService.findUserIdByRefreshToken(SAMPLE_INCOMING_REFRESH_TOKEN)).thenReturn(SAMPLE_USER_ID);
    when(userRepository.findById(SAMPLE_USER_ID)).thenReturn(Optional.of(sampleUser));
    when(jwtService.generateAccessToken(sampleUser)).thenReturn(SAMPLE_NEW_ACCESS_TOKEN);

    AccessTokenRefreshedResponse response = authService.generateNewAccessToken(SAMPLE_INCOMING_REFRESH_TOKEN);

    assertThat(response.getAccessToken(), is(SAMPLE_NEW_ACCESS_TOKEN));
  }

  @Test
  void generateNewAccessTokenThrowsForbiddenExceptionWhenRefreshTokenNotFoundInRedis() {
    when(redisService.findUserIdByRefreshToken(SAMPLE_INCOMING_REFRESH_TOKEN)).thenReturn(null);

    ForbiddenException thrown = assertThrows(ForbiddenException.class,
        () -> authService.generateNewAccessToken(SAMPLE_INCOMING_REFRESH_TOKEN));

    assertThat(thrown.getMessage(), containsString(INVALID_REFRESH_TOKEN_MESSAGE));
    verify(userRepository, never()).findById(any());
    verify(jwtService, never()).generateAccessToken(any());
  }

  @Test
  void generateNewAccessTokenThrowsUserNotFoundExceptionWhenUserMissingForResolvedId() {
    when(redisService.findUserIdByRefreshToken(SAMPLE_INCOMING_REFRESH_TOKEN)).thenReturn(SAMPLE_USER_ID);
    when(userRepository.findById(SAMPLE_USER_ID)).thenReturn(Optional.empty());

    UserNotFoundException thrown = assertThrows(UserNotFoundException.class,
        () -> authService.generateNewAccessToken(SAMPLE_INCOMING_REFRESH_TOKEN));

    assertThat(thrown.getMessage(), containsString(USER_NOT_FOUND_MESSAGE));
    verify(jwtService, never()).generateAccessToken(any());
  }
}