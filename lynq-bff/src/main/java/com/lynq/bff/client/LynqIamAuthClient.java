package com.lynq.bff.client;

import com.lynq.bff.client.request.EmailLoginRequest;
import com.lynq.bff.client.request.RegisterUserRequest;
import com.lynq.bff.client.request.UpdatePasswordRequest;
import com.lynq.bff.client.request.UsernameLoginRequest;
import com.lynq.bff.client.response.AccessTokenRefreshedResponse;
import com.lynq.bff.client.response.AuthUserResponse;
import com.lynq.bff.client.response.CheckEmailResponse;
import com.lynq.bff.client.response.CheckUsernameResponse;
import com.lynq.bff.controller.response.GlobalRestResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "lynqIamAuth", url = "${lynq.iam.url}")
public interface LynqIamAuthClient {

  String REQUEST_UUID_HEADER = "lynq-request-uuid";
  String AUTHORIZATION_HEADER = "Authorization";

  @PostMapping("/auth/register")
  GlobalRestResponse<AuthUserResponse> register(
      @RequestBody RegisterUserRequest request,
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid);

  @PostMapping("/auth/login/username")
  GlobalRestResponse<AuthUserResponse> loginByUsername(
      @RequestBody UsernameLoginRequest request,
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid);

  @PostMapping("/auth/login/email")
  GlobalRestResponse<AuthUserResponse> loginByEmail(
      @RequestBody EmailLoginRequest request,
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid);

  @PostMapping("/auth/refresh")
  GlobalRestResponse<AccessTokenRefreshedResponse> refresh(
      @RequestHeader(AUTHORIZATION_HEADER) String refreshToken,
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid);

  @PatchMapping("/auth/update-password")
  GlobalRestResponse<AuthUserResponse> updatePassword(
      @RequestBody UpdatePasswordRequest request,
      @RequestHeader(AUTHORIZATION_HEADER) String authorization,
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid);

  @GetMapping("/auth/check-username")
  GlobalRestResponse<CheckUsernameResponse> checkUsername(
      @RequestParam("username") String username,
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid);

  @GetMapping("/auth/check-email")
  GlobalRestResponse<CheckEmailResponse> checkEmail(
      @RequestParam("email") String email,
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid);
}
