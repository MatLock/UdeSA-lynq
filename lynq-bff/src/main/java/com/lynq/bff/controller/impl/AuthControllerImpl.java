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
import com.lynq.bff.exceptions.UnauthorizedException;
import com.lynq.bff.security.RefreshTokenCookie;
import com.lynq.bff.service.AuthService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
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
  private static final String BEARER_PREFIX = "Bearer ";
  private static final String MISSING_REFRESH_COOKIE = "Missing refresh token cookie";

  private final AuthService authService;
  private final RefreshTokenCookie refreshTokenCookie;

  public AuthControllerImpl(AuthService authService, RefreshTokenCookie refreshTokenCookie) {
    this.authService = authService;
    this.refreshTokenCookie = refreshTokenCookie;
  }

  @Override
  @PostMapping("/register")
  public ResponseEntity<GlobalRestResponse<AuthUserResponse>> register(
      @RequestBody RegisterUserRequest request,
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid) {
    AuthUserResponse registered = authService.register(request, requestUuid);

    return withRefreshCookie(HttpStatus.CREATED, registered, false);
  }

  @Override
  @PostMapping("/login/username")
  public ResponseEntity<GlobalRestResponse<AuthUserResponse>> loginByUsername(
      @RequestBody UsernameLoginRequest request,
      @RequestParam(defaultValue = "false") boolean remember,
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid) {
    AuthUserResponse user = authService.loginByUsername(request, requestUuid);

    return withRefreshCookie(HttpStatus.OK, user, remember);
  }

  @Override
  @PostMapping("/login/email")
  public ResponseEntity<GlobalRestResponse<AuthUserResponse>> loginByEmail(
      @RequestBody EmailLoginRequest request,
      @RequestParam(defaultValue = "false") boolean remember,
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid) {
    AuthUserResponse user = authService.loginByEmail(request, requestUuid);

    return withRefreshCookie(HttpStatus.OK, user, remember);
  }

  @Override
  @PostMapping("/refresh")
  public ResponseEntity<GlobalRestResponse<AccessTokenRefreshedResponse>> refresh(
      @CookieValue(name = RefreshTokenCookie.NAME, required = false) String refreshToken,
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid) {
    if (refreshToken == null || refreshToken.isBlank()) {
      throw new UnauthorizedException(MISSING_REFRESH_COOKIE);
    }
    AccessTokenRefreshedResponse refreshed =
        authService.refresh(BEARER_PREFIX + refreshToken, requestUuid);

    return ResponseEntity
        .status(HttpStatus.OK)
        .body(new GlobalRestResponse<>(true, refreshed));
  }

  @Override
  @PostMapping("/logout")
  public ResponseEntity<Void> logout() {
    return ResponseEntity
        .status(HttpStatus.NO_CONTENT)
        .header(HttpHeaders.SET_COOKIE, refreshTokenCookie.clear().toString())
        .build();
  }

  @Override
  @PatchMapping("/update-password")
  public ResponseEntity<GlobalRestResponse<AuthUserResponse>> updatePassword(
      @RequestBody UpdatePasswordRequest request,
      @RequestParam(defaultValue = "false") boolean remember,
      @RequestHeader(AUTHORIZATION_HEADER) String authorization,
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid) {
    AuthUserResponse user = authService.updatePassword(request, authorization, requestUuid);

    return withRefreshCookie(HttpStatus.OK, user, remember);
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

  private ResponseEntity<GlobalRestResponse<AuthUserResponse>> withRefreshCookie(
      HttpStatus status, AuthUserResponse user, boolean remember) {
    return ResponseEntity
        .status(status)
        .header(HttpHeaders.SET_COOKIE,
            refreshTokenCookie.issue(user.getRefreshToken(), remember).toString())
        .body(new GlobalRestResponse<>(true, user));
  }
}
