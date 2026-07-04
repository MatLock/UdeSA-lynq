package com.lynq.iam.controller.impl;

import com.lynq.iam.controller.impl.AuthControllerImpl;
import com.lynq.iam.controller.request.CreateUserRequest;
import com.lynq.iam.controller.request.EmailUserLogin;
import com.lynq.iam.controller.request.UserUpdatePasswordRequest;
import com.lynq.iam.controller.request.UsernameLogin;
import com.lynq.iam.controller.response.AccessTokenRefreshedResponse;
import com.lynq.iam.controller.response.CheckEmailResponse;
import com.lynq.iam.controller.response.CheckUsernameResponse;
import com.lynq.iam.controller.response.GlobalRestResponse;
import com.lynq.iam.controller.response.UserInfoRestResponse;
import com.lynq.iam.controller.response.UserRestResponse;
import com.lynq.iam.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthControllerImplTest {

  private static final String SAMPLE_USERNAME = "johndoe";
  private static final String SAMPLE_PASSWORD = "P@ssw0rd123";
  private static final String SAMPLE_EMAIL = "johndoe@example.com";
  private static final String SAMPLE_NEW_PASSWORD = "N3wStr0ngPass!";
  private static final String RAW_ACCESS_TOKEN = "eyJhbGciOiJIUzI1NiJ9.access.token";
  private static final String RAW_REFRESH_TOKEN = "eyJhbGciOiJIUzI1NiJ9.refresh.token";
  private static final String BEARER_PREFIX = "Bearer ";
  private static final String AUTH_HEADER_WITH_BEARER_ACCESS = BEARER_PREFIX + RAW_ACCESS_TOKEN;
  private static final String AUTH_HEADER_WITHOUT_BEARER_ACCESS = RAW_ACCESS_TOKEN;
  private static final String AUTH_HEADER_WITH_BEARER_REFRESH = BEARER_PREFIX + RAW_REFRESH_TOKEN;
  private static final boolean SERVICE_RESULT_SUCCESS_FLAG = true;
  private static final boolean SERVICE_RETURNS_TOKEN_VALID = true;
  private static final MediaType EXPECTED_CONTENT_TYPE = MediaType.APPLICATION_JSON;
  private static final HttpStatus EXPECTED_CREATED_STATUS = HttpStatus.CREATED;
  private static final HttpStatus EXPECTED_OK_STATUS = HttpStatus.OK;

  @Mock
  private AuthService authService;

  @Mock
  private UserRestResponse userRestResponse;

  @Mock
  private UserInfoRestResponse userInfoRestResponse;

  @Mock
  private AccessTokenRefreshedResponse accessTokenRefreshedResponse;

  @Mock
  private CheckUsernameResponse checkUsernameResponse;

  @Mock
  private CheckEmailResponse checkEmailResponse;

  private AuthControllerImpl authController;

  @BeforeEach
  void setUp() {
    authController = new AuthControllerImpl(authService);
  }

  @Test
  void createUserReturnsCreatedStatusWithJsonContentTypeAndServiceResultBody() {
    CreateUserRequest request = new CreateUserRequest(SAMPLE_USERNAME, SAMPLE_PASSWORD, SAMPLE_EMAIL);
    when(authService.registerUser(SAMPLE_USERNAME, SAMPLE_PASSWORD, SAMPLE_EMAIL))
        .thenReturn(userRestResponse);

    ResponseEntity<GlobalRestResponse<UserRestResponse>> result = authController.createUser(request);

    assertThat(result, is(notNullValue()));
    assertThat(result.getStatusCode(), is(EXPECTED_CREATED_STATUS));
    assertThat(result.getHeaders().getContentType(), is(EXPECTED_CONTENT_TYPE));
    assertThat(result.getBody(), is(notNullValue()));
    assertThat(result.getBody().isSuccess(), is(SERVICE_RESULT_SUCCESS_FLAG));
    assertThat(result.getBody().getData(), is(sameInstance(userRestResponse)));
  }

  @Test
  void createUserDelegatesToAuthServiceWithRequestFields() {
    CreateUserRequest request = new CreateUserRequest(SAMPLE_USERNAME, SAMPLE_PASSWORD, SAMPLE_EMAIL);
    when(authService.registerUser(SAMPLE_USERNAME, SAMPLE_PASSWORD, SAMPLE_EMAIL))
        .thenReturn(userRestResponse);

    authController.createUser(request);

    verify(authService).registerUser(SAMPLE_USERNAME, SAMPLE_PASSWORD, SAMPLE_EMAIL);
  }

  @Test
  void loginByUsernameReturnsOkStatusWithServiceResultBody() {
    UsernameLogin request = new UsernameLogin(SAMPLE_USERNAME, SAMPLE_PASSWORD);
    when(authService.loginByUsername(SAMPLE_USERNAME, SAMPLE_PASSWORD)).thenReturn(userRestResponse);

    ResponseEntity<GlobalRestResponse<UserRestResponse>> result = authController.loginByUsername(request);

    assertThat(result.getStatusCode(), is(EXPECTED_OK_STATUS));
    assertThat(result.getHeaders().getContentType(), is(EXPECTED_CONTENT_TYPE));
    assertThat(result.getBody().isSuccess(), is(SERVICE_RESULT_SUCCESS_FLAG));
    assertThat(result.getBody().getData(), is(sameInstance(userRestResponse)));
  }

  @Test
  void loginByUsernameDelegatesToAuthServiceWithRequestFields() {
    UsernameLogin request = new UsernameLogin(SAMPLE_USERNAME, SAMPLE_PASSWORD);
    when(authService.loginByUsername(SAMPLE_USERNAME, SAMPLE_PASSWORD)).thenReturn(userRestResponse);

    authController.loginByUsername(request);

    verify(authService).loginByUsername(SAMPLE_USERNAME, SAMPLE_PASSWORD);
  }

  @Test
  void loginByEmailReturnsOkStatusWithServiceResultBody() {
    EmailUserLogin request = new EmailUserLogin(SAMPLE_EMAIL, SAMPLE_PASSWORD);
    when(authService.loginByEmail(SAMPLE_EMAIL, SAMPLE_PASSWORD)).thenReturn(userRestResponse);

    ResponseEntity<GlobalRestResponse<UserRestResponse>> result = authController.loginByEmail(request);

    assertThat(result.getStatusCode(), is(EXPECTED_OK_STATUS));
    assertThat(result.getHeaders().getContentType(), is(EXPECTED_CONTENT_TYPE));
    assertThat(result.getBody().isSuccess(), is(SERVICE_RESULT_SUCCESS_FLAG));
    assertThat(result.getBody().getData(), is(sameInstance(userRestResponse)));
  }

  @Test
  void loginByEmailDelegatesToAuthServiceWithRequestFields() {
    EmailUserLogin request = new EmailUserLogin(SAMPLE_EMAIL, SAMPLE_PASSWORD);
    when(authService.loginByEmail(SAMPLE_EMAIL, SAMPLE_PASSWORD)).thenReturn(userRestResponse);

    authController.loginByEmail(request);

    verify(authService).loginByEmail(SAMPLE_EMAIL, SAMPLE_PASSWORD);
  }

  @Test
  void updatePasswordStripsBearerPrefixBeforeDelegatingToAuthService() {
    UserUpdatePasswordRequest request = new UserUpdatePasswordRequest();
    request.setNewPassword(SAMPLE_NEW_PASSWORD);
    when(authService.updatePassword(RAW_ACCESS_TOKEN, SAMPLE_NEW_PASSWORD)).thenReturn(userRestResponse);

    authController.updatePassword(AUTH_HEADER_WITH_BEARER_ACCESS, request);

    verify(authService).updatePassword(RAW_ACCESS_TOKEN, SAMPLE_NEW_PASSWORD);
  }

  @Test
  void updatePasswordUsesRawTokenWhenBearerPrefixAbsent() {
    UserUpdatePasswordRequest request = new UserUpdatePasswordRequest();
    request.setNewPassword(SAMPLE_NEW_PASSWORD);
    when(authService.updatePassword(RAW_ACCESS_TOKEN, SAMPLE_NEW_PASSWORD)).thenReturn(userRestResponse);

    authController.updatePassword(AUTH_HEADER_WITHOUT_BEARER_ACCESS, request);

    verify(authService).updatePassword(RAW_ACCESS_TOKEN, SAMPLE_NEW_PASSWORD);
  }

  @Test
  void updatePasswordReturnsOkStatusWithServiceResultBody() {
    UserUpdatePasswordRequest request = new UserUpdatePasswordRequest();
    request.setNewPassword(SAMPLE_NEW_PASSWORD);
    when(authService.updatePassword(RAW_ACCESS_TOKEN, SAMPLE_NEW_PASSWORD)).thenReturn(userRestResponse);

    ResponseEntity<GlobalRestResponse<UserRestResponse>> result =
        authController.updatePassword(AUTH_HEADER_WITH_BEARER_ACCESS, request);

    assertThat(result.getStatusCode(), is(EXPECTED_OK_STATUS));
    assertThat(result.getHeaders().getContentType(), is(EXPECTED_CONTENT_TYPE));
    assertThat(result.getBody().isSuccess(), is(SERVICE_RESULT_SUCCESS_FLAG));
    assertThat(result.getBody().getData(), is(sameInstance(userRestResponse)));
  }

  @Test
  void isAccessTokenValidStripsBearerPrefixBeforeDelegatingToAuthService() {
    when(authService.isAccessTokenValid(RAW_ACCESS_TOKEN)).thenReturn(SERVICE_RETURNS_TOKEN_VALID);

    authController.isAccessTokenValid(AUTH_HEADER_WITH_BEARER_ACCESS);

    verify(authService).isAccessTokenValid(RAW_ACCESS_TOKEN);
  }

  @Test
  void isAccessTokenValidReturnsOkStatusWithServiceBooleanResult() {
    when(authService.isAccessTokenValid(RAW_ACCESS_TOKEN)).thenReturn(SERVICE_RETURNS_TOKEN_VALID);

    ResponseEntity<GlobalRestResponse<Boolean>> result =
        authController.isAccessTokenValid(AUTH_HEADER_WITH_BEARER_ACCESS);

    assertThat(result.getStatusCode(), is(EXPECTED_OK_STATUS));
    assertThat(result.getHeaders().getContentType(), is(EXPECTED_CONTENT_TYPE));
    assertThat(result.getBody().isSuccess(), is(SERVICE_RESULT_SUCCESS_FLAG));
    assertThat(result.getBody().getData(), is(SERVICE_RETURNS_TOKEN_VALID));
  }

  @Test
  void generateNewAccessTokenStripsBearerPrefixBeforeDelegatingToAuthService() {
    when(authService.generateNewAccessToken(RAW_REFRESH_TOKEN)).thenReturn(accessTokenRefreshedResponse);

    authController.generateNewAccessToken(AUTH_HEADER_WITH_BEARER_REFRESH);

    verify(authService).generateNewAccessToken(RAW_REFRESH_TOKEN);
  }

  @Test
  void generateNewAccessTokenReturnsOkStatusWithServiceResultBody() {
    when(authService.generateNewAccessToken(RAW_REFRESH_TOKEN)).thenReturn(accessTokenRefreshedResponse);

    ResponseEntity<GlobalRestResponse<AccessTokenRefreshedResponse>> result =
        authController.generateNewAccessToken(AUTH_HEADER_WITH_BEARER_REFRESH);

    assertThat(result.getStatusCode(), is(EXPECTED_OK_STATUS));
    assertThat(result.getHeaders().getContentType(), is(EXPECTED_CONTENT_TYPE));
    assertThat(result.getBody().isSuccess(), is(SERVICE_RESULT_SUCCESS_FLAG));
    assertThat(result.getBody().getData(), is(sameInstance(accessTokenRefreshedResponse)));
  }

  @Test
  void obtainUserInfoFromTokenStripsBearerPrefixBeforeDelegatingToAuthService() {
    when(authService.obtainUserInfoFromToken(RAW_ACCESS_TOKEN)).thenReturn(userInfoRestResponse);

    authController.obtainUserInfoFromToken(AUTH_HEADER_WITH_BEARER_ACCESS);

    verify(authService).obtainUserInfoFromToken(RAW_ACCESS_TOKEN);
  }

  @Test
  void obtainUserInfoFromTokenReturnsOkStatusWithServiceResultBody() {
    when(authService.obtainUserInfoFromToken(RAW_ACCESS_TOKEN)).thenReturn(userInfoRestResponse);

    ResponseEntity<GlobalRestResponse<UserInfoRestResponse>> result =
        authController.obtainUserInfoFromToken(AUTH_HEADER_WITH_BEARER_ACCESS);

    assertThat(result.getStatusCode(), is(EXPECTED_OK_STATUS));
    assertThat(result.getHeaders().getContentType(), is(EXPECTED_CONTENT_TYPE));
    assertThat(result.getBody().isSuccess(), is(SERVICE_RESULT_SUCCESS_FLAG));
    assertThat(result.getBody().getData(), is(sameInstance(userInfoRestResponse)));
  }

  @Test
  void checkUsernameReturnsOkStatusWithServiceResultBody() {
    when(authService.checkUsername(SAMPLE_USERNAME)).thenReturn(checkUsernameResponse);

    ResponseEntity<GlobalRestResponse<CheckUsernameResponse>> result =
        authController.checkUsername(SAMPLE_USERNAME);

    assertThat(result.getStatusCode(), is(EXPECTED_OK_STATUS));
    assertThat(result.getHeaders().getContentType(), is(EXPECTED_CONTENT_TYPE));
    assertThat(result.getBody().isSuccess(), is(SERVICE_RESULT_SUCCESS_FLAG));
    assertThat(result.getBody().getData(), is(sameInstance(checkUsernameResponse)));
  }

  @Test
  void checkUsernameDelegatesToAuthServiceWithUsername() {
    when(authService.checkUsername(SAMPLE_USERNAME)).thenReturn(checkUsernameResponse);

    authController.checkUsername(SAMPLE_USERNAME);

    verify(authService).checkUsername(SAMPLE_USERNAME);
  }

  @Test
  void checkEmailReturnsOkStatusWithServiceResultBody() {
    when(authService.checkEmail(SAMPLE_EMAIL)).thenReturn(checkEmailResponse);

    ResponseEntity<GlobalRestResponse<CheckEmailResponse>> result =
        authController.checkEmail(SAMPLE_EMAIL);

    assertThat(result.getStatusCode(), is(EXPECTED_OK_STATUS));
    assertThat(result.getHeaders().getContentType(), is(EXPECTED_CONTENT_TYPE));
    assertThat(result.getBody().isSuccess(), is(SERVICE_RESULT_SUCCESS_FLAG));
    assertThat(result.getBody().getData(), is(sameInstance(checkEmailResponse)));
  }

  @Test
  void checkEmailDelegatesToAuthServiceWithEmail() {
    when(authService.checkEmail(SAMPLE_EMAIL)).thenReturn(checkEmailResponse);

    authController.checkEmail(SAMPLE_EMAIL);

    verify(authService).checkEmail(SAMPLE_EMAIL);
  }
}