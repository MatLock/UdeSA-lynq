package com.lynq.backend.client;

import com.lynq.backend.client.request.CreateFileDownloadBatchRequest;
import com.lynq.backend.client.request.CreateFileUploadRequest;
import com.lynq.backend.client.response.CreateFileDownloadResponse;
import com.lynq.backend.client.response.CreateFileUploadResponse;
import com.lynq.backend.client.response.StoredFileResponse;
import com.lynq.backend.controller.response.GlobalRestResponse;
import java.util.Map;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

/**
 * HTTP client for the lynq-file-storage service, which owns every file the platform stores: it
 * registers the metadata, signs the upload and download URLs and holds the bucket credentials.
 * This service only ever handles the file ids it returns. The {@code lynq-request-uuid} header is
 * forwarded on every call so logs can be correlated across services.
 */
@FeignClient(name = "lynqFileStorage", url = "${lynq.file-storage.url}")
public interface LynqFileStorageClient {

  String REQUEST_UUID_HEADER = "lynq-request-uuid";

  @PostMapping("/files/upload-url")
  GlobalRestResponse<CreateFileUploadResponse> createUpload(
      @RequestBody CreateFileUploadRequest request,
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid);

  @PostMapping("/files/{fileId}/confirm")
  GlobalRestResponse<StoredFileResponse> confirmUpload(
      @PathVariable("fileId") String fileId,
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid);

  @GetMapping("/files/{fileId}/download-url")
  GlobalRestResponse<CreateFileDownloadResponse> createDownloadUrl(
      @PathVariable("fileId") String fileId,
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid);

  @PostMapping("/files/download-urls")
  GlobalRestResponse<Map<String, String>> createDownloadUrls(
      @RequestBody CreateFileDownloadBatchRequest request,
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid);

  @DeleteMapping("/files/{fileId}")
  void deleteFile(
      @PathVariable("fileId") String fileId,
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid);
}
