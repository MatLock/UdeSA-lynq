package com.lynq.filestorage.controller.impl;

import com.lynq.filestorage.controller.request.CreateFileDownloadBatchRequest;
import com.lynq.filestorage.controller.request.CreateFileUploadRequest;
import com.lynq.filestorage.controller.response.CreateFileDownloadRestResponse;
import com.lynq.filestorage.controller.response.CreateFileUploadRestResponse;
import com.lynq.filestorage.controller.response.FileRestResponse;
import com.lynq.filestorage.controller.response.GlobalRestResponse;
import com.lynq.filestorage.enums.StoredFileStatus;
import com.lynq.filestorage.model.StoredFileEntity;
import com.lynq.filestorage.service.FileService;
import com.lynq.filestorage.service.PreSignedUploadUrl;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileControllerImplTest {

  private static final String FILE_NAME = "cv.pdf";
  private static final String CONTENT_TYPE = "application/pdf";
  private static final String FILE_ID = "0195f2c1-3b1a-7c2d-9f31-3f6a5f2c9d41";
  private static final String S3_KEY = "lynq/" + FILE_ID + "/" + FILE_NAME;
  private static final String UPLOAD_URL = "https://s3.local/upload?signature=abc";
  private static final String DOWNLOAD_URL = "https://s3.local/download?signature=abc";

  @Mock
  private FileService fileService;

  private FileControllerImpl fileController;

  @BeforeEach
  void setUp() {
    fileController = new FileControllerImpl(fileService);
  }

  @Test
  void createUploadReturnsCreatedWithTheFileIdAndThePreSignedUploadUrl() {
    StoredFileEntity storedFile = buildStoredFile(StoredFileStatus.PENDING);
    when(fileService.createUpload(any(CreateFileUploadRequest.class))).thenReturn(storedFile);
    when(fileService.createUploadUrl(storedFile)).thenReturn(new PreSignedUploadUrl(S3_KEY, UPLOAD_URL));

    ResponseEntity<GlobalRestResponse<CreateFileUploadRestResponse>> response =
        fileController.createUpload(buildRequest());

    assertThat(response.getStatusCode(), is(HttpStatus.CREATED));
    assertThat(response.getBody().isSuccess(), is(true));
    assertThat(response.getBody().getData().getFileId(), is(FILE_ID));
    assertThat(response.getBody().getData().getS3Key(), is(S3_KEY));
    assertThat(response.getBody().getData().getUploadUrl(), is(UPLOAD_URL));
  }

  @Test
  void confirmUploadReturnsOkWithTheAvailableFileMetadata() {
    when(fileService.confirmUpload(FILE_ID)).thenReturn(buildStoredFile(StoredFileStatus.AVAILABLE));

    ResponseEntity<GlobalRestResponse<FileRestResponse>> response = fileController.confirmUpload(FILE_ID);

    assertThat(response.getStatusCode(), is(HttpStatus.OK));
    assertThat(response.getBody().getData().getFileId(), is(FILE_ID));
    assertThat(response.getBody().getData().getFileName(), is(FILE_NAME));
    assertThat(response.getBody().getData().getStatus(), is(StoredFileStatus.AVAILABLE));
  }

  @Test
  void createDownloadUrlReturnsOkWithThePreSignedGetUrl() {
    StoredFileEntity storedFile = buildStoredFile(StoredFileStatus.AVAILABLE);
    when(fileService.findFile(FILE_ID)).thenReturn(storedFile);
    when(fileService.createDownloadUrl(storedFile)).thenReturn(DOWNLOAD_URL);

    ResponseEntity<GlobalRestResponse<CreateFileDownloadRestResponse>> response =
        fileController.createDownloadUrl(FILE_ID);

    assertThat(response.getStatusCode(), is(HttpStatus.OK));
    assertThat(response.getBody().getData().getFileId(), is(FILE_ID));
    assertThat(response.getBody().getData().getS3Key(), is(S3_KEY));
    assertThat(response.getBody().getData().getDownloadUrl(), is(DOWNLOAD_URL));
  }

  @Test
  void createDownloadUrlsReturnsOkWithTheUrlsKeyedByFileId() {
    when(fileService.createDownloadUrls(List.of(FILE_ID))).thenReturn(Map.of(FILE_ID, DOWNLOAD_URL));

    ResponseEntity<GlobalRestResponse<Map<String, String>>> response = fileController.createDownloadUrls(
        CreateFileDownloadBatchRequest.builder().fileIds(List.of(FILE_ID)).build());

    assertThat(response.getStatusCode(), is(HttpStatus.OK));
    assertThat(response.getBody().getData(), is(Map.of(FILE_ID, DOWNLOAD_URL)));
  }

  private CreateFileUploadRequest buildRequest() {
    return CreateFileUploadRequest.builder()
        .fileName(FILE_NAME)
        .contentType(CONTENT_TYPE)
        .build();
  }

  private StoredFileEntity buildStoredFile(StoredFileStatus status) {
    LocalDateTime now = LocalDateTime.now();
    return StoredFileEntity.builder()
        .id(FILE_ID)
        .fileName(FILE_NAME)
        .contentType(CONTENT_TYPE)
        .s3Key(S3_KEY)
        .status(status)
        .createdOn(now)
        .updatedOn(now)
        .build();
  }
}
