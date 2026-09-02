package com.lynq.bff.client;

import com.lynq.bff.client.request.CreateResumeRequest;
import com.lynq.bff.client.response.DeletedResumeResponse;
import com.lynq.bff.client.response.UserResponse;
import com.lynq.bff.controller.response.GlobalRestResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "lynqBackend", url = "${lynq.backend.url}")
public interface LynqBackendClient {

  String REQUEST_UUID_HEADER = "lynq-request-uuid";
  String AUTHORIZATION_HEADER = "Authorization";

  @GetMapping("/dmz/user")
  GlobalRestResponse<UserResponse> getUser(
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid,
      @RequestHeader(AUTHORIZATION_HEADER) String authorization);

  @PostMapping("/dmz/user/confirm-upload-resume")
  void confirmResumeUpload(
      @RequestParam("file-id") String fileId,
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid,
      @RequestHeader(AUTHORIZATION_HEADER) String authorization);

  @PostMapping("/dmz/user/resume")
  GlobalRestResponse<Object> createResume(
      @RequestBody CreateResumeRequest request,
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid,
      @RequestHeader(AUTHORIZATION_HEADER) String authorization);

  @DeleteMapping("/dmz/user/resume/{resumeId}")
  GlobalRestResponse<DeletedResumeResponse> deleteResume(
      @PathVariable("resumeId") String resumeId,
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid,
      @RequestHeader(AUTHORIZATION_HEADER) String authorization);
}
