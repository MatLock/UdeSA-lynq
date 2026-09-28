package com.lynq.bff.service;

import com.lynq.bff.client.LynqFileStorageClient;
import com.lynq.bff.client.request.CreateFileDownloadBatchRequest;
import com.lynq.bff.client.request.CreateFileUploadRequest;
import com.lynq.bff.client.response.CreateFileDownloadResponse;
import com.lynq.bff.client.response.CreateFileUploadResponse;
import com.lynq.bff.client.response.FileResponse;
import java.util.Map;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

@Service
@Log4j2
public class FileService {

  private static final String UPLOAD_NOT_REGISTERED = "The file upload could not be registered";
  private static final String UPLOAD_NOT_CONFIRMED = "The file upload could not be confirmed";
  private static final String FILE_UNREADABLE = "The file could not be read";
  private static final String DOWNLOAD_URL_NOT_ISSUED = "The download url could not be issued";
  private static final String DOWNLOAD_URLS_NOT_ISSUED = "The download urls could not be issued";
  private static final String FILE_NOT_DELETED = "The file could not be deleted";

  private final LynqFileStorageClient lynqFileStorageClient;

  public FileService(LynqFileStorageClient lynqFileStorageClient) {
    this.lynqFileStorageClient = lynqFileStorageClient;
  }

  public CreateFileUploadResponse createUpload(CreateFileUploadRequest request, Caller caller) {
    log.info("message= Registering file upload, user_id={}, file_name={}",
        caller.userId(), request.getFileName());

    return DownstreamErrors.call(
        () -> lynqFileStorageClient
            .createUpload(request, caller.requestUuid(), caller.authorization())
            .getData(),
        UPLOAD_NOT_REGISTERED);
  }

  public FileResponse confirmUpload(String fileId, Caller caller) {
    log.info("message= Confirming file upload, user_id={}, file_id={}", caller.userId(), fileId);

    return DownstreamErrors.call(
        () -> lynqFileStorageClient
            .confirmUpload(fileId, caller.requestUuid(), caller.authorization())
            .getData(),
        UPLOAD_NOT_CONFIRMED);
  }

  public FileResponse findOwnedFile(String fileId, Caller caller) {
    return DownstreamErrors.call(
        () -> lynqFileStorageClient
            .findOwnedFile(fileId, caller.requestUuid(), caller.authorization())
            .getData(),
        FILE_UNREADABLE);
  }

  public CreateFileDownloadResponse createDownloadUrl(String fileId, Caller caller) {
    return DownstreamErrors.call(
        () -> lynqFileStorageClient
            .createDownloadUrl(fileId, caller.requestUuid(), caller.authorization())
            .getData(),
        DOWNLOAD_URL_NOT_ISSUED);
  }

  public Map<String, String> createDownloadUrls(CreateFileDownloadBatchRequest request,
                                                Caller caller) {
    return DownstreamErrors.call(
        () -> lynqFileStorageClient
            .createDownloadUrls(request, caller.requestUuid(), caller.authorization())
            .getData(),
        DOWNLOAD_URLS_NOT_ISSUED);
  }

  public void deleteFile(String fileId, Caller caller) {
    log.info("message= Deleting file, user_id={}, file_id={}", caller.userId(), fileId);

    DownstreamErrors.run(
        () -> lynqFileStorageClient.deleteFile(fileId, caller.requestUuid(), caller.authorization()),
        FILE_NOT_DELETED);
  }
}
