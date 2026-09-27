package com.lynq.bff.controller.impl;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lynq.bff.client.request.EmailLoginRequest;
import com.lynq.bff.client.request.RegisterUserRequest;
import com.lynq.bff.client.request.UpdatePasswordRequest;
import com.lynq.bff.client.request.UsernameLoginRequest;
import com.lynq.bff.client.response.AccessTokenRefreshedResponse;
import com.lynq.bff.client.response.AuthUserResponse;
import com.lynq.bff.client.response.CheckEmailResponse;
import com.lynq.bff.client.response.CheckUsernameResponse;
import com.lynq.bff.controller.response.GlobalRestResponse;
import com.lynq.bff.enums.UserRole;
import com.lynq.bff.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class AuthControllerImplTest {

  private static final String REQUEST_UUID = "018f9c3a-2b1d-7c4e-9a6f-1e2d3c4b5a99";
  private static final String AUTHORIZATION = "Bearer access-token";
  private static final String REFRESH_CREDENTIAL = "Bearer 8f14e45f-ceea-467a-9ae4-9b3f4a1c2d3e";
  private static final String USER_ID = "11111111-1111-1111-1111-111111111111";
  private static final String USERNAME = "janedoe";
  private static final String EMAIL = "jane@lynq.com";

  @Mock
  private AuthService authService;

  private AuthControllerImpl authController;

  @BeforeEach
  void setUp() {
    authController = new AuthControllerImpl(authService);
  }

  @Test
  void answersARegisteredAccountWithCreated() {
    RegisterUserRequest request = RegisterUserRequest.builder()
        .username(USERNAME)
        .role(UserRole.R_CANDIDATE)
        .build();
    AuthUserResponse registered = AuthUserResponse.builder().id(USER_ID).build();
    when(authService.register(request, REQUEST_UUID)).thenReturn(registered);

    ResponseEntity<GlobalRestResponse<AuthUserResponse>> response =
        authController.register(request, REQUEST_UUID);

    assertThat(response.getStatusCode(), is(HttpStatus.CREATED));
    assertThat(response.getBody().getData(), is(sameInstance(registered)));
    assertThat(response.getBody().isSuccess(), is(true));
  }

  @Test
  void answersAUsernameLoginWithOk() {
    UsernameLoginRequest request = UsernameLoginRequest.builder().username(USERNAME).build();
    AuthUserResponse session = AuthUserResponse.builder().id(USER_ID).build();
    when(authService.loginByUsername(request, REQUEST_UUID)).thenReturn(session);

    ResponseEntity<GlobalRestResponse<AuthUserResponse>> response =
        authController.loginByUsername(request, REQUEST_UUID);

    assertThat(response.getStatusCode(), is(HttpStatus.OK));
    assertThat(response.getBody().getData(), is(sameInstance(session)));
  }

  @Test
  void answersAnEmailLoginWithOk() {
    EmailLoginRequest request = EmailLoginRequest.builder().email(EMAIL).build();
    AuthUserResponse session = AuthUserResponse.builder().id(USER_ID).build();
    when(authService.loginByEmail(request, REQUEST_UUID)).thenReturn(session);

    ResponseEntity<GlobalRestResponse<AuthUserResponse>> response =
        authController.loginByEmail(request, REQUEST_UUID);

    assertThat(response.getStatusCode(), is(HttpStatus.OK));
    assertThat(response.getBody().getData(), is(sameInstance(session)));
  }

  /** The refresh credential is not a JWT, so it is handed on exactly as the caller sent it. */
  @Test
  void handsTheRefreshCredentialOnUntouched() {
    AccessTokenRefreshedResponse refreshed = new AccessTokenRefreshedResponse();
    when(authService.refresh(REFRESH_CREDENTIAL, REQUEST_UUID)).thenReturn(refreshed);

    ResponseEntity<GlobalRestResponse<AccessTokenRefreshedResponse>> response =
        authController.refresh(REFRESH_CREDENTIAL, REQUEST_UUID);

    assertThat(response.getStatusCode(), is(HttpStatus.OK));
    assertThat(response.getBody().getData(), is(sameInstance(refreshed)));
    verify(authService).refresh(eq(REFRESH_CREDENTIAL), eq(REQUEST_UUID));
  }

  @Test
  void answersAPasswordUpdateWithOk() {
    UpdatePasswordRequest request = new UpdatePasswordRequest();
    AuthUserResponse user = AuthUserResponse.builder().id(USER_ID).build();
    when(authService.updatePassword(request, AUTHORIZATION, REQUEST_UUID)).thenReturn(user);

    ResponseEntity<GlobalRestResponse<AuthUserResponse>> response =
        authController.updatePassword(request, AUTHORIZATION, REQUEST_UUID);

    assertThat(response.getStatusCode(), is(HttpStatus.OK));
    assertThat(response.getBody().getData(), is(sameInstance(user)));
  }

  @Test
  void answersTheUsernameCheckWithOk() {
    CheckUsernameResponse checked = CheckUsernameResponse.builder().valid(true).build();
    when(authService.checkUsername(USERNAME, REQUEST_UUID)).thenReturn(checked);

    ResponseEntity<GlobalRestResponse<CheckUsernameResponse>> response =
        authController.checkUsername(USERNAME, REQUEST_UUID);

    assertThat(response.getStatusCode(), is(HttpStatus.OK));
    assertThat(response.getBody().getData(), is(sameInstance(checked)));
  }

  @Test
  void answersTheEmailCheckWithOk() {
    CheckEmailResponse checked = CheckEmailResponse.builder().valid(false).build();
    when(authService.checkEmail(EMAIL, REQUEST_UUID)).thenReturn(checked);

    ResponseEntity<GlobalRestResponse<CheckEmailResponse>> response =
        authController.checkEmail(EMAIL, REQUEST_UUID);

    assertThat(response.getStatusCode(), is(HttpStatus.OK));
    assertThat(response.getBody().getData(), is(sameInstance(checked)));
  }
}
