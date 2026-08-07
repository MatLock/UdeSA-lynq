package com.lynq.backend.service;

import com.lynq.backend.aspect.AuditLog;
import com.lynq.backend.client.LynqFileStorageClient;
import com.lynq.backend.client.request.CreateFileDownloadBatchRequest;
import com.lynq.backend.client.request.CreateFileUploadRequest;
import com.lynq.backend.client.response.CreateFileUploadResponse;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

/**
 * Every file the platform stores lives in lynq-file-storage: it owns the bucket, the object keys
 * and the metadata, and this service only keeps the file ids it hands back. Nothing here talks to
 * S3 directly.
 */
@Service
public class FileStorageService {

  /** Set by {@code RequestUuidFilter} from the incoming {@code lynq-request-uuid} header. */
  private static final String MDC_REQUEST_ID = "requestId";

  /** lynq-file-storage signs at most this many download URLs per call. */
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
        lynqFileStorageClient.createUpload(request, requestUuid()).getData();

    return new RegisteredUpload(response.getFileId(), response.getUploadUrl());
  }

  /**
   * Promotes a file to AVAILABLE once the browser has finished the pre-signed PUT. lynq-file-storage
   * rejects the call while the object is still missing from the bucket.
   */
  @AuditLog
  public void confirmUpload(String fileId) {
    lynqFileStorageClient.confirmUpload(fileId, requestUuid());
  }

  /**
   * Signs a read URL for a stored file, or returns {@code null} when the owning record has no file
   * attached, which is the common case for users and companies without a profile image.
   */
  @AuditLog
  public String obtainDownloadUrl(String fileId) {
    if (isBlank(fileId)) {
      return null;
    }
    return lynqFileStorageClient.createDownloadUrl(fileId, requestUuid())
        .getData()
        .getDownloadUrl();
  }

  /**
   * Signs read URLs for a whole page of records in as few round-trips as possible, keyed by file
   * id. Blank ids are dropped before the call and ids lynq-file-storage does not know are simply
   * absent from the result, so callers get {@code null} for them.
   */
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
    lynqFileStorageClient.deleteFile(fileId, requestUuid());
  }

  private static boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  /**
   * Forwards the uuid of the request being served so lynq-file-storage logs land under the same
   * correlation id. Calls made outside a request (tests, scheduled work) get a fresh one.
   */
  private static String requestUuid() {
    String requestUuid = MDC.get(MDC_REQUEST_ID);
    return isBlank(requestUuid) ? UUID.randomUUID().toString() : requestUuid;
  }

}
