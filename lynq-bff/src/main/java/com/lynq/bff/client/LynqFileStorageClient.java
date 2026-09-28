package com.lynq.bff.client;

import com.lynq.bff.client.request.CreateFileDownloadBatchRequest;
import com.lynq.bff.client.request.CreateFileUploadRequest;
import com.lynq.bff.client.response.CreateFileDownloadResponse;
import com.lynq.bff.client.response.CreateFileUploadResponse;
import com.lynq.bff.client.response.FileResponse;
import com.lynq.bff.controller.response.GlobalRestResponse;
import java.util.Map;
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
  String AUTHORIZATION_HEADER = "Authorization";

  @PostMapping("/dmz/files/upload-url")
  GlobalRestResponse<CreateFileUploadResponse> createUpload(
      @RequestBody CreateFileUploadRequest request,
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid,
      @RequestHeader(AUTHORIZATION_HEADER) String authorization);

  @PostMapping("/dmz/files/{fileId}/confirm")
  GlobalRestResponse<FileResponse> confirmUpload(
      @PathVariable("fileId") String fileId,
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid,
      @RequestHeader(AUTHORIZATION_HEADER) String authorization);

  @GetMapping("/dmz/files/{fileId}")
  GlobalRestResponse<FileResponse> findOwnedFile(
      @PathVariable("fileId") String fileId,
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid,
      @RequestHeader(AUTHORIZATION_HEADER) String authorization);

  @GetMapping("/dmz/files/{fileId}/download-url")
  GlobalRestResponse<CreateFileDownloadResponse> createDownloadUrl(
      @PathVariable("fileId") String fileId,
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid,
      @RequestHeader(AUTHORIZATION_HEADER) String authorization);

  @PostMapping("/dmz/files/download-urls")
  GlobalRestResponse<Map<String, String>> createDownloadUrls(
      @RequestBody CreateFileDownloadBatchRequest request,
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid,
      @RequestHeader(AUTHORIZATION_HEADER) String authorization);

  @DeleteMapping("/dmz/files/{fileId}")
  void deleteFile(
      @PathVariable("fileId") String fileId,
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid,
      @RequestHeader(AUTHORIZATION_HEADER) String authorization);
}
