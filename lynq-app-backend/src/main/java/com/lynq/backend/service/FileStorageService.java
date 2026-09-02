package com.lynq.backend.service;

import com.lynq.backend.aspect.AuditLog;
import com.lynq.backend.client.LynqFileStorageClient;
import com.lynq.backend.client.request.CreateFileDownloadBatchRequest;
import com.lynq.backend.client.request.CreateFileUploadRequest;
import com.lynq.backend.client.response.CreateFileUploadResponse;
import com.lynq.backend.security.LynqUserPrincipal;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class FileStorageService {

  private static final String MDC_REQUEST_ID = "requestId";

  private static final int DOWNLOAD_BATCH_SIZE = 100;

  private final LynqFileStorageClient lynqFileStorageClient;

  public FileStorageService(LynqFileStorageClient lynqFileStorageClient) {
    this.lynqFileStorageClient = lynqFileStorageClient;
  }

  @AuditLog
  public RegisteredUpload registerUpload(String fileName) {
    CreateFileUploadRequest request = CreateFileUploadRequest.builder()
        .fileName(fileName)
        .build();

    CreateFileUploadResponse response =
        lynqFileStorageClient.createUpload(request, requestUuid(), authenticatedUserId()).getData();

    return new RegisteredUpload(response.getFileId(), response.getUploadUrl());
  }

  @AuditLog
  public void confirmUpload(String fileId) {
    lynqFileStorageClient.confirmUpload(fileId, requestUuid(), authenticatedUserId());
  }

  @AuditLog
  public String obtainDownloadUrl(String fileId) {
    if (isBlank(fileId)) {
      return null;
    }
    return lynqFileStorageClient.createDownloadUrl(fileId, requestUuid())
        .getData()
        .getDownloadUrl();
  }

  @AuditLog
  public Map<String, String> obtainDownloadUrls(Collection<String> fileIds) {
    List<String> distinctIds = fileIds.stream()
        .filter(fileId -> !isBlank(fileId))
        .distinct()
        .toList();

    Map<String, String> downloadUrls = new HashMap<>();
    for (int from = 0; from < distinctIds.size(); from += DOWNLOAD_BATCH_SIZE) {
      List<String> batch =
          distinctIds.subList(from, Math.min(from + DOWNLOAD_BATCH_SIZE, distinctIds.size()));
      downloadUrls.putAll(lynqFileStorageClient.createDownloadUrls(
          CreateFileDownloadBatchRequest.builder().fileIds(batch).build(), requestUuid()).getData());
    }

    return downloadUrls;
  }

  @AuditLog
  public void deleteFile(String fileId) {
    if (isBlank(fileId)) {
      return;
    }
    lynqFileStorageClient.deleteFile(fileId, requestUuid(), authenticatedUserId());
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  private static String requestUuid() {
    String requestUuid = MDC.get(MDC_REQUEST_ID);
    return isBlank(requestUuid) ? UUID.randomUUID().toString() : requestUuid;
  }

  private static String authenticatedUserId() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
    if (authentication == null || !(authentication.getPrincipal() instanceof LynqUserPrincipal principal)) {
      throw new IllegalStateException("No authenticated user to attribute the file operation to");
    }
    return principal.getId();
  }

}
