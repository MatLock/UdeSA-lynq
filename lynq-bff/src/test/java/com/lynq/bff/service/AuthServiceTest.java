package com.lynq.bff.service;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.lynq.bff.client.LynqIamAuthClient;
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
import com.lynq.bff.exceptions.BadGatewayException;
import com.lynq.bff.exceptions.ConflictException;
import com.lynq.bff.exceptions.UnauthorizedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

  private static final String REQUEST_UUID = "018f9c3a-2b1d-7c4e-9a6f-1e2d3c4b5a99";
  private static final String AUTHORIZATION = "Bearer access-token";
  private static final String REFRESH_CREDENTIAL = "Bearer 8f14e45f-ceea-467a-9ae4-9b3f4a1c2d3e";
  private static final String USER_ID = "11111111-1111-1111-1111-111111111111";
  private static final String USERNAME = "janedoe";
  private static final String EMAIL = "jane@lynq.com";

  @Mock
  private LynqIamAuthClient lynqIamAuthClient;

  private AuthService authService;

  @BeforeEach
  void setUp() {
    authService = new AuthService(lynqIamAuthClient);
  }

  @Test
  void registersAnAccount() {
    RegisterUserRequest request = RegisterUserRequest.builder()
        .username(USERNAME)
        .email(EMAIL)
        .role(UserRole.R_CANDIDATE)
        .build();
    AuthUserResponse registered = AuthUserResponse.builder().id(USER_ID).build();
    when(lynqIamAuthClient.register(request, REQUEST_UUID))
        .thenReturn(new GlobalRestResponse<>(true, registered));

    assertThat(authService.register(request, REQUEST_UUID), is(sameInstance(registered)));
  }

  @Test
  void logsInByUsername() {
    UsernameLoginRequest request = UsernameLoginRequest.builder().username(USERNAME).build();
    AuthUserResponse session = AuthUserResponse.builder().id(USER_ID).build();
    when(lynqIamAuthClient.loginByUsername(request, REQUEST_UUID))
        .thenReturn(new GlobalRestResponse<>(true, session));

    assertThat(authService.loginByUsername(request, REQUEST_UUID), is(sameInstance(session)));
  }

  @Test
  void logsInByEmail() {
    EmailLoginRequest request = EmailLoginRequest.builder().email(EMAIL).build();
    AuthUserResponse session = AuthUserResponse.builder().id(USER_ID).build();
    when(lynqIamAuthClient.loginByEmail(request, REQUEST_UUID))
        .thenReturn(new GlobalRestResponse<>(true, session));

    assertThat(authService.loginByEmail(request, REQUEST_UUID), is(sameInstance(session)));
  }

  /**
   * The refresh credential is opaque to this service — it is not a JWT and there is no secret here
   * that could check it — so it crosses exactly as the caller sent it.
   */
  @Test
  void refreshesWithTheCredentialUntouched() {
    AccessTokenRefreshedResponse refreshed = new AccessTokenRefreshedResponse();
    when(lynqIamAuthClient.refresh(REFRESH_CREDENTIAL, REQUEST_UUID))
        .thenReturn(new GlobalRestResponse<>(true, refreshed));

    assertThat(authService.refresh(REFRESH_CREDENTIAL, REQUEST_UUID), is(sameInstance(refreshed)));
  }

  @Test
  void updatesThePasswordOfTheCallerTheTokenNames() {
    UpdatePasswordRequest request = new UpdatePasswordRequest();
    AuthUserResponse user = AuthUserResponse.builder().id(USER_ID).build();
    when(lynqIamAuthClient.updatePassword(request, AUTHORIZATION, REQUEST_UUID))
        .thenReturn(new GlobalRestResponse<>(true, user));

    assertThat(authService.updatePassword(request, AUTHORIZATION, REQUEST_UUID),
        is(sameInstance(user)));
  }

  @Test
  void checksAUsername() {
    CheckUsernameResponse checked = CheckUsernameResponse.builder().valid(true).build();
    when(lynqIamAuthClient.checkUsername(USERNAME, REQUEST_UUID))
        .thenReturn(new GlobalRestResponse<>(true, checked));

    assertThat(authService.checkUsername(USERNAME, REQUEST_UUID), is(sameInstance(checked)));
  }

  @Test
  void checksAnEmail() {
    CheckEmailResponse checked = CheckEmailResponse.builder().valid(false).build();
    when(lynqIamAuthClient.checkEmail(EMAIL, REQUEST_UUID))
        .thenReturn(new GlobalRestResponse<>(true, checked));

    assertThat(authService.checkEmail(EMAIL, REQUEST_UUID), is(sameInstance(checked)));
  }

  @Test
  void keepsTheUnauthorizedWhenTheCredentialsDoNotMatch() {
    EmailLoginRequest request = EmailLoginRequest.builder().email(EMAIL).build();
    when(lynqIamAuthClient.loginByEmail(request, REQUEST_UUID))
        .thenThrow(FeignErrors.status(401, """
            {"success": false, "reason": "Invalid password"}"""));

    UnauthorizedException thrown = assertThrows(UnauthorizedException.class,
        () -> authService.loginByEmail(request, REQUEST_UUID));

    assertThat(thrown.getMessage(), is("Invalid password"));
  }

  @Test
  void keepsTheConflictWhenTheUsernameIsTaken() {
    RegisterUserRequest request = RegisterUserRequest.builder().username(USERNAME).build();
    when(lynqIamAuthClient.register(request, REQUEST_UUID))
        .thenThrow(FeignErrors.status(409, """
            {"success": false, "reason": "Username is already taken"}"""));

    ConflictException thrown = assertThrows(ConflictException.class,
        () -> authService.register(request, REQUEST_UUID));

    assertThat(thrown.getMessage(), is("Username is already taken"));
  }

  @Test
  void answersBadGatewayWhenLynqIamCannotBeReached() {
    when(lynqIamAuthClient.checkEmail(EMAIL, REQUEST_UUID)).thenThrow(FeignErrors.unreachable());

    BadGatewayException thrown = assertThrows(BadGatewayException.class,
        () -> authService.checkEmail(EMAIL, REQUEST_UUID));

    assertThat(thrown.getMessage(), is("The email could not be checked"));
  }
}
