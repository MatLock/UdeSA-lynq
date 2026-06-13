package com.lynq.iam.controller.impl;

import com.lynq.iam.controller.AuthController;
import com.lynq.iam.controller.request.CreateUserRequest;
import com.lynq.iam.controller.request.EmailUserLogin;
import com.lynq.iam.controller.request.UserUpdatePasswordRequest;
import com.lynq.iam.controller.request.UsernameLogin;
import com.lynq.iam.controller.response.AccessTokenRefreshedResponse;
import com.lynq.iam.controller.response.GlobalRestResponse;
import com.lynq.iam.controller.response.UserInfoRestResponse;
import com.lynq.iam.controller.response.UserRestResponse;
import com.lynq.iam.service.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController("/auth")
@Validated
public class AuthControllerImpl implements AuthController {

  public static final String BEARER_PREFIX = "Bearer ";
  private final AuthService authService;

  public AuthControllerImpl(AuthService authService) {
    this.authService = authService;
  }

  @Override
  @PostMapping("/register")
  public ResponseEntity<GlobalRestResponse<UserRestResponse>> createUser(@RequestBody CreateUserRequest request) {
    GlobalRestResponse<UserRestResponse> body = new GlobalRestResponse<>(true, authService.registerUser(
        request.getUsername(),
        request.getPassword(),
        request.getEmail()
    ));
    return ResponseEntity.status(HttpStatus.CREATED)
        .contentType(MediaType.APPLICATION_JSON)
        .body(body);
  }

  @Override
  @PostMapping("/login/username")
  public ResponseEntity<GlobalRestResponse<UserRestResponse>> loginByUsername(@RequestBody UsernameLogin usernameLoginRequest) {
    GlobalRestResponse<UserRestResponse> body = new GlobalRestResponse<>(true, authService.loginByUsername(
        usernameLoginRequest.getUsername(),
        usernameLoginRequest.getPassword()
    ));
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_JSON)
        .body(body);
  }

  @Override
  @PostMapping("/login/email")
  public ResponseEntity<GlobalRestResponse<UserRestResponse>> loginByEmail(@RequestBody EmailUserLogin emailUserLoginRequest) {
    GlobalRestResponse<UserRestResponse> body = new GlobalRestResponse<>(true, authService.loginByEmail(
        emailUserLoginRequest.getEmail(),
        emailUserLoginRequest.getPassword()
    ));
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_JSON)
        .body(body);
  }

  @Override
  @PatchMapping("/update-password")
  public ResponseEntity<GlobalRestResponse<UserRestResponse>> updatePassword(
      @RequestHeader("Authorization") String accessToken,
      @RequestBody UserUpdatePasswordRequest userUpdatePasswordRequest) {
    String token = accessToken.startsWith(BEARER_PREFIX) ? accessToken.substring(7) : accessToken;
    GlobalRestResponse<UserRestResponse> body = new GlobalRestResponse<>(true,
        authService.updatePassword(token, userUpdatePasswordRequest.getNewPassword()));
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_JSON)
        .body(body);
  }

  @Override
  @GetMapping("/validate")
  public ResponseEntity<GlobalRestResponse<Boolean>> isAccessTokenValid(
      @RequestHeader("Authorization") String accessToken) {
    String token = accessToken.startsWith(BEARER_PREFIX) ? accessToken.substring(7) : accessToken;
    GlobalRestResponse<Boolean> body = new GlobalRestResponse<>(true, authService.isAccessTokenValid(token));
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_JSON)
        .body(body);
  }

  @Override
  @PostMapping("/refresh")
  public ResponseEntity<GlobalRestResponse<AccessTokenRefreshedResponse>> generateNewAccessToken(
      @RequestHeader("Authorization") String refreshToken) {
    String token = refreshToken.startsWith(BEARER_PREFIX) ? refreshToken.substring(7) : refreshToken;
    GlobalRestResponse<AccessTokenRefreshedResponse> body = new GlobalRestResponse<>(true,
        authService.generateNewAccessToken(token));
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_JSON)
        .body(body);
  }

  @Override
  @GetMapping("/userinfo")
  public ResponseEntity<GlobalRestResponse<UserInfoRestResponse>> obtainUserInfoFromToken(
      @RequestHeader("Authorization") String accessToken) {
    String token = accessToken.startsWith(BEARER_PREFIX) ? accessToken.substring(7) : accessToken;
    GlobalRestResponse<UserInfoRestResponse> body = new GlobalRestResponse<>(true,
        authService.obtainUserInfoFromToken(token));
    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_JSON)
        .body(body);
  }
}