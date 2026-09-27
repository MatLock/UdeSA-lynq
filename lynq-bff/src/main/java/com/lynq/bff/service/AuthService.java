package com.lynq.bff.service;

import com.lynq.bff.client.LynqIamAuthClient;
import com.lynq.bff.client.request.EmailLoginRequest;
import com.lynq.bff.client.request.RegisterUserRequest;
import com.lynq.bff.client.request.UpdatePasswordRequest;
import com.lynq.bff.client.request.UsernameLoginRequest;
import com.lynq.bff.client.response.AccessTokenRefreshedResponse;
import com.lynq.bff.client.response.AuthUserResponse;
import com.lynq.bff.client.response.CheckEmailResponse;
import com.lynq.bff.client.response.CheckUsernameResponse;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

@Service
@Log4j2
public class AuthService {

  private static final String REGISTER_FAILED = "The user could not be registered";
  private static final String LOGIN_FAILED = "The credentials could not be checked";
  private static final String REFRESH_FAILED = "The access token could not be refreshed";
  private static final String PASSWORD_NOT_UPDATED = "The password could not be updated";
  private static final String USERNAME_NOT_CHECKED = "The username could not be checked";
  private static final String EMAIL_NOT_CHECKED = "The email could not be checked";

  private final LynqIamAuthClient lynqIamAuthClient;

  public AuthService(LynqIamAuthClient lynqIamAuthClient) {
    this.lynqIamAuthClient = lynqIamAuthClient;
  }

  public AuthUserResponse register(RegisterUserRequest request, String requestUuid) {
    log.info("message= Registering a user, username={}", request.getUsername());

    return DownstreamErrors.call(
        () -> lynqIamAuthClient.register(request, requestUuid).getData(), REGISTER_FAILED);
  }

  public AuthUserResponse loginByUsername(UsernameLoginRequest request, String requestUuid) {
    log.info("message= Logging in by username, username={}", request.getUsername());

    return DownstreamErrors.call(
        () -> lynqIamAuthClient.loginByUsername(request, requestUuid).getData(), LOGIN_FAILED);
  }

  public AuthUserResponse loginByEmail(EmailLoginRequest request, String requestUuid) {
    log.info("message= Logging in by email");

    return DownstreamErrors.call(
        () -> lynqIamAuthClient.loginByEmail(request, requestUuid).getData(), LOGIN_FAILED);
  }

  public AccessTokenRefreshedResponse refresh(String refreshToken, String requestUuid) {
    return DownstreamErrors.call(
        () -> lynqIamAuthClient.refresh(refreshToken, requestUuid).getData(), REFRESH_FAILED);
  }

  public AuthUserResponse updatePassword(UpdatePasswordRequest request, String authorization,
                                         String requestUuid) {
    log.info("message= Updating a password");

    return DownstreamErrors.call(
        () -> lynqIamAuthClient.updatePassword(request, authorization, requestUuid).getData(),
        PASSWORD_NOT_UPDATED);
  }

  public CheckUsernameResponse checkUsername(String username, String requestUuid) {
    return DownstreamErrors.call(
        () -> lynqIamAuthClient.checkUsername(username, requestUuid).getData(), USERNAME_NOT_CHECKED);
  }

  public CheckEmailResponse checkEmail(String email, String requestUuid) {
    return DownstreamErrors.call(
        () -> lynqIamAuthClient.checkEmail(email, requestUuid).getData(), EMAIL_NOT_CHECKED);
  }
}
