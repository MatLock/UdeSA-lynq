package com.lynq.bff.controller.impl;

import com.lynq.bff.client.request.CreateFileDownloadBatchRequest;
import com.lynq.bff.client.request.CreateFileUploadRequest;
import com.lynq.bff.client.response.CreateFileDownloadResponse;
import com.lynq.bff.client.response.CreateFileUploadResponse;
import com.lynq.bff.client.response.FileResponse;
import com.lynq.bff.controller.FileController;
import com.lynq.bff.controller.response.GlobalRestResponse;
import com.lynq.bff.security.LynqUserPrincipal;
import com.lynq.bff.service.Caller;
import com.lynq.bff.service.FileService;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/files")
public class FileControllerImpl implements FileController {

  private static final String REQUEST_UUID_HEADER = "lynq-request-uuid";

  private final FileService fileService;

  public FileControllerImpl(FileService fileService) {
    this.fileService = fileService;
  }

  @Override
  @PostMapping("/upload-url")
  public ResponseEntity<GlobalRestResponse<CreateFileUploadResponse>> createUpload(
      @RequestBody CreateFileUploadRequest request,
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid,
      @AuthenticationPrincipal LynqUserPrincipal principal) {
    CreateFileUploadResponse upload =
        fileService.createUpload(request, caller(principal, requestUuid));

    return ResponseEntity
        .status(HttpStatus.CREATED)
        .body(new GlobalRestResponse<>(true, upload));
  }

  @Override
  @PostMapping("/{fileId}/confirm")
  public ResponseEntity<GlobalRestResponse<FileResponse>> confirmUpload(
      @PathVariable String fileId,
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid,
      @AuthenticationPrincipal LynqUserPrincipal principal) {
    FileResponse file = fileService.confirmUpload(fileId, caller(principal, requestUuid));

    return ResponseEntity
        .status(HttpStatus.OK)
        .body(new GlobalRestResponse<>(true, file));
  }

  @Override
  @GetMapping("/{fileId}")
  public ResponseEntity<GlobalRestResponse<FileResponse>> findOwnedFile(
      @PathVariable String fileId,
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid,
      @AuthenticationPrincipal LynqUserPrincipal principal) {
    FileResponse file = fileService.findOwnedFile(fileId, caller(principal, requestUuid));

    return ResponseEntity
        .status(HttpStatus.OK)
        .body(new GlobalRestResponse<>(true, file));
  }

  @Override
  @GetMapping("/{fileId}/download-url")
  public ResponseEntity<GlobalRestResponse<CreateFileDownloadResponse>> createDownloadUrl(
      @PathVariable String fileId,
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid,
      @AuthenticationPrincipal LynqUserPrincipal principal) {
    CreateFileDownloadResponse download =
        fileService.createDownloadUrl(fileId, caller(principal, requestUuid));

    return ResponseEntity
        .status(HttpStatus.OK)
        .body(new GlobalRestResponse<>(true, download));
  }

  @Override
  @PostMapping("/download-urls")
  public ResponseEntity<GlobalRestResponse<Map<String, String>>> createDownloadUrls(
      @RequestBody CreateFileDownloadBatchRequest request,
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid,
      @AuthenticationPrincipal LynqUserPrincipal principal) {
    Map<String, String> downloadUrls =
        fileService.createDownloadUrls(request, caller(principal, requestUuid));

    return ResponseEntity
        .status(HttpStatus.OK)
        .body(new GlobalRestResponse<>(true, downloadUrls));
  }

  @Override
  @DeleteMapping("/{fileId}")
  public ResponseEntity<Void> deleteFile(
      @PathVariable String fileId,
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid,
      @AuthenticationPrincipal LynqUserPrincipal principal) {
    fileService.deleteFile(fileId, caller(principal, requestUuid));

    return ResponseEntity.noContent().build();
  }

  private static Caller caller(LynqUserPrincipal principal, String requestUuid) {
    return new Caller(principal.getId(), requestUuid, principal.getAuthorization());
  }
}
