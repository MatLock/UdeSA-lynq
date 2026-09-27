package com.lynq.bff.controller.impl;

import com.lynq.bff.client.request.EmailLoginRequest;
import com.lynq.bff.client.request.RegisterUserRequest;
import com.lynq.bff.client.request.UpdatePasswordRequest;
import com.lynq.bff.client.request.UsernameLoginRequest;
import com.lynq.bff.client.response.AccessTokenRefreshedResponse;
import com.lynq.bff.client.response.AuthUserResponse;
import com.lynq.bff.client.response.CheckEmailResponse;
import com.lynq.bff.client.response.CheckUsernameResponse;
import com.lynq.bff.controller.AuthController;
import com.lynq.bff.controller.response.GlobalRestResponse;
import com.lynq.bff.service.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthControllerImpl implements AuthController {

  private static final String REQUEST_UUID_HEADER = "lynq-request-uuid";
  private static final String AUTHORIZATION_HEADER = "Authorization";

  private final AuthService authService;

  public AuthControllerImpl(AuthService authService) {
    this.authService = authService;
  }

  @Override
  @PostMapping("/register")
  public ResponseEntity<GlobalRestResponse<AuthUserResponse>> register(
      @RequestBody RegisterUserRequest request,
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid) {
    AuthUserResponse registered = authService.register(request, requestUuid);

    return ResponseEntity
        .status(HttpStatus.CREATED)
        .body(new GlobalRestResponse<>(true, registered));
  }

  @Override
  @PostMapping("/login/username")
  public ResponseEntity<GlobalRestResponse<AuthUserResponse>> loginByUsername(
      @RequestBody UsernameLoginRequest request,
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid) {
    AuthUserResponse user = authService.loginByUsername(request, requestUuid);

    return ResponseEntity
        .status(HttpStatus.OK)
        .body(new GlobalRestResponse<>(true, user));
  }

  @Override
  @PostMapping("/login/email")
  public ResponseEntity<GlobalRestResponse<AuthUserResponse>> loginByEmail(
      @RequestBody EmailLoginRequest request,
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid) {
    AuthUserResponse user = authService.loginByEmail(request, requestUuid);

    return ResponseEntity
        .status(HttpStatus.OK)
        .body(new GlobalRestResponse<>(true, user));
  }

  @Override
  @PostMapping("/refresh")
  public ResponseEntity<GlobalRestResponse<AccessTokenRefreshedResponse>> refresh(
      @RequestHeader(AUTHORIZATION_HEADER) String refreshToken,
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid) {
    AccessTokenRefreshedResponse refreshed = authService.refresh(refreshToken, requestUuid);

    return ResponseEntity
        .status(HttpStatus.OK)
        .body(new GlobalRestResponse<>(true, refreshed));
  }

  @Override
  @PatchMapping("/update-password")
  public ResponseEntity<GlobalRestResponse<AuthUserResponse>> updatePassword(
      @RequestBody UpdatePasswordRequest request,
      @RequestHeader(AUTHORIZATION_HEADER) String authorization,
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid) {
    AuthUserResponse user = authService.updatePassword(request, authorization, requestUuid);

    return ResponseEntity
        .status(HttpStatus.OK)
        .body(new GlobalRestResponse<>(true, user));
  }

  @Override
  @GetMapping("/check-username")
  public ResponseEntity<GlobalRestResponse<CheckUsernameResponse>> checkUsername(
      @RequestParam String username,
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid) {
    CheckUsernameResponse checked = authService.checkUsername(username, requestUuid);

    return ResponseEntity
        .status(HttpStatus.OK)
        .body(new GlobalRestResponse<>(true, checked));
  }

  @Override
  @GetMapping("/check-email")
  public ResponseEntity<GlobalRestResponse<CheckEmailResponse>> checkEmail(
      @RequestParam String email,
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid) {
    CheckEmailResponse checked = authService.checkEmail(email, requestUuid);

    return ResponseEntity
        .status(HttpStatus.OK)
        .body(new GlobalRestResponse<>(true, checked));
  }
}
