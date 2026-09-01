package com.lynq.backend.client;

import com.lynq.backend.client.response.UserInfoResponse;
import com.lynq.backend.controller.response.GlobalRestResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "lynqIam", url = "${lynq.iam.url}")
public interface LynqIamClient {

  String AUTHORIZATION_HEADER = "Authorization";
  String REQUEST_UUID_HEADER = "lynq-request-uuid";

  @GetMapping("/auth/user-info")
  GlobalRestResponse<UserInfoResponse> getUserInfo(
      @RequestHeader(AUTHORIZATION_HEADER) String authorization,
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid);
}
