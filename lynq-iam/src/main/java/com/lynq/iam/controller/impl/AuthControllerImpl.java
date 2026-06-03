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
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
public class AuthControllerImpl implements AuthController {

  public static final String BEARER_PREFIX = "Bearer ";
  private final AuthService authService;

  public AuthControllerImpl(AuthService authService) {
    this.authService = authService;
  }

  @Override
  @PostMapping("/register")
  @ResponseStatus(HttpStatus.CREATED)
  public GlobalRestResponse<UserRestResponse> createUser(@Valid @RequestBody CreateUserRequest request) {
    return new GlobalRestResponse<>(true, authService.registerUser(
        request.getUsername(),
        request.getPassword(),
        request.getEmail()
    ));
  }

  @Override
  @PostMapping("/login/username")
  public GlobalRestResponse<UserRestResponse> loginByUsername(@Valid @RequestBody UsernameLogin usernameLoginRequest) {
    return new GlobalRestResponse<>(true, authService.loginByUsername(
        usernameLoginRequest.getUsername(),
        usernameLoginRequest.getPassword()
    ));
  }

  @Override
  @PostMapping("/login/email")
  public GlobalRestResponse<UserRestResponse> loginByEmail(@Valid @RequestBody EmailUserLogin emailUserLoginRequest) {
    return new GlobalRestResponse<>(true, authService.loginByEmail(
        emailUserLoginRequest.getEmail(),
        emailUserLoginRequest.getPassword()
    ));
  }

  @Override
  @PatchMapping("/update-password")
  public GlobalRestResponse<UserRestResponse> updatePassword(
      @RequestHeader("Authorization") String accessToken,
      @Valid @RequestBody UserUpdatePasswordRequest userUpdatePasswordRequest) {
    String token = accessToken.startsWith(BEARER_PREFIX) ? accessToken.substring(7) : accessToken;
    return new GlobalRestResponse<>(true, authService.updatePassword(token, userUpdatePasswordRequest.getNewPassword()));
  }

  @Override
  @GetMapping("/validate")
  public GlobalRestResponse<Boolean> isAccessTokenValid(@RequestHeader("Authorization") String accessToken) {
    String token = accessToken.startsWith(BEARER_PREFIX) ? accessToken.substring(7) : accessToken;
    return new GlobalRestResponse<>(true, authService.isAccessTokenValid(token));
  }

  @Override
  @PostMapping("/refresh")
  public GlobalRestResponse<AccessTokenRefreshedResponse> generateNewAccessToken(@RequestHeader("Authorization") String refreshToken) {
    String token = refreshToken.startsWith(BEARER_PREFIX) ? refreshToken.substring(7) : refreshToken;
    return new GlobalRestResponse<>(true, authService.generateNewAccessToken(token));
  }

  @Override
  @GetMapping("/userinfo")
  public GlobalRestResponse<UserInfoRestResponse> obtainUserInfoFromToken(@RequestHeader("Authorization") String accessToken) {
    String token = accessToken.startsWith(BEARER_PREFIX) ? accessToken.substring(7) : accessToken;
    return new GlobalRestResponse<>(true, authService.obtainUserInfoFromToken(token));
  }
}
