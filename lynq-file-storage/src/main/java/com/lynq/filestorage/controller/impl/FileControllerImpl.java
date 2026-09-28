package com.lynq.filestorage.controller.impl;

import com.lynq.filestorage.aspect.AuditLog;
import com.lynq.filestorage.controller.request.CreateFileDownloadBatchRequest;
import com.lynq.filestorage.controller.request.CreateFileUploadRequest;
import com.lynq.filestorage.controller.response.CreateFileDownloadRestResponse;
import com.lynq.filestorage.controller.response.CreateFileUploadRestResponse;
import com.lynq.filestorage.controller.response.FileRestResponse;
import com.lynq.filestorage.controller.response.GlobalRestResponse;
import com.lynq.filestorage.model.StoredFileEntity;
import com.lynq.filestorage.security.LynqUserPrincipal;
import com.lynq.filestorage.service.FileService;
import com.lynq.filestorage.service.PreSignedUploadUrl;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/dmz/files")
@Validated
public class FileControllerImpl implements com.lynq.filestorage.controller.FileController {

  private final FileService fileService;

  public FileControllerImpl(FileService fileService) {
    this.fileService = fileService;
  }

  @Override
  @PostMapping("/upload-url")
  @AuditLog
  public ResponseEntity<GlobalRestResponse<CreateFileUploadRestResponse>> createUpload(
      @Valid @RequestBody CreateFileUploadRequest request,
      @AuthenticationPrincipal LynqUserPrincipal principal) {
    StoredFileEntity storedFile = fileService.createUpload(request, principal.getId());
    PreSignedUploadUrl preSignedUploadUrl = fileService.createUploadUrl(storedFile);

    CreateFileUploadRestResponse response = CreateFileUploadRestResponse.builder()
        .fileId(storedFile.getId())
        .s3Key(preSignedUploadUrl.s3Path())
        .uploadUrl(preSignedUploadUrl.url())
        .build();

    return ResponseEntity
        .status(HttpStatus.CREATED)
        .body(new GlobalRestResponse<>(true, response));
  }

  @Override
  @PostMapping("/{fileId}/confirm")
  @AuditLog
  public ResponseEntity<GlobalRestResponse<FileRestResponse>> confirmUpload(
      @PathVariable String fileId, @AuthenticationPrincipal LynqUserPrincipal principal) {
    StoredFileEntity storedFile = fileService.confirmUpload(fileId, principal.getId());

    return ResponseEntity.ok(new GlobalRestResponse<>(true, toResponse(storedFile)));
  }

  @Override
  @GetMapping("/{fileId}")
  @AuditLog
  public ResponseEntity<GlobalRestResponse<FileRestResponse>> findOwnedFile(
      @PathVariable String fileId, @AuthenticationPrincipal LynqUserPrincipal principal) {
    StoredFileEntity storedFile = fileService.findOwnedFile(fileId, principal.getId());

    return ResponseEntity.ok(new GlobalRestResponse<>(true, toResponse(storedFile)));
  }

  @Override
  @GetMapping("/{fileId}/download-url")
  @AuditLog
  public ResponseEntity<GlobalRestResponse<CreateFileDownloadRestResponse>> createDownloadUrl(
      @PathVariable String fileId) {
    StoredFileEntity storedFile = fileService.findFile(fileId);

    CreateFileDownloadRestResponse response = CreateFileDownloadRestResponse.builder()
        .fileId(storedFile.getId())
        .s3Key(storedFile.getS3Key())
        .downloadUrl(fileService.createDownloadUrl(storedFile))
        .build();

    return ResponseEntity.ok(new GlobalRestResponse<>(true, response));
  }

  @Override
  @PostMapping("/download-urls")
  @AuditLog
  public ResponseEntity<GlobalRestResponse<Map<String, String>>> createDownloadUrls(
      @Valid @RequestBody CreateFileDownloadBatchRequest request) {
    Map<String, String> downloadUrls = fileService.createDownloadUrls(request.getFileIds());

    return ResponseEntity.ok(new GlobalRestResponse<>(true, downloadUrls));
  }

  private FileRestResponse toResponse(StoredFileEntity storedFile) {
    return FileRestResponse.builder()
        .fileId(storedFile.getId())
        .fileName(storedFile.getFileName())
        .contentType(storedFile.getContentType())
        .s3Key(storedFile.getS3Key())
        .status(storedFile.getStatus())
        .createdOn(storedFile.getCreatedOn())
        .updatedOn(storedFile.getUpdatedOn())
        .build();
  }

  @Override
  @DeleteMapping("/{fileId}")
  @AuditLog
  public ResponseEntity<Void> deleteFile(
      @PathVariable String fileId, @AuthenticationPrincipal LynqUserPrincipal principal) {
    fileService.deleteFile(fileId, principal.getId());

    return ResponseEntity.noContent().build();
  }

}
