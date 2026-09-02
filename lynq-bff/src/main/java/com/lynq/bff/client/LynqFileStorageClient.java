package com.lynq.bff.client;

import com.lynq.bff.client.request.CreateFileUploadRequest;
import com.lynq.bff.client.response.CreateFileDownloadResponse;
import com.lynq.bff.client.response.CreateFileUploadResponse;
import com.lynq.bff.controller.response.GlobalRestResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "lynqFileStorage", url = "${lynq.file-storage.url}")
public interface LynqFileStorageClient {

  String REQUEST_UUID_HEADER = "lynq-request-uuid";
  String USER_ID_HEADER = "user-id";

  @PostMapping("/dmz/files/upload-url")
  GlobalRestResponse<CreateFileUploadResponse> createUpload(
      @RequestBody CreateFileUploadRequest request,
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid,
      @RequestHeader(USER_ID_HEADER) String userId);

  @PostMapping("/dmz/files/{fileId}/confirm")
  void confirmUpload(
      @PathVariable("fileId") String fileId,
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid,
      @RequestHeader(USER_ID_HEADER) String userId);

  @GetMapping("/dmz/files/{fileId}/download-url")
  GlobalRestResponse<CreateFileDownloadResponse> createDownloadUrl(
      @PathVariable("fileId") String fileId,
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid);

  @DeleteMapping("/dmz/files/{fileId}")
  void deleteFile(
      @PathVariable("fileId") String fileId,
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid,
      @RequestHeader(USER_ID_HEADER) String userId);
}
