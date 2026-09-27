package com.lynq.bff.controller.impl;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lynq.bff.client.request.CreateFileDownloadBatchRequest;
import com.lynq.bff.client.request.CreateFileUploadRequest;
import com.lynq.bff.client.response.CreateFileDownloadResponse;
import com.lynq.bff.client.response.CreateFileUploadResponse;
import com.lynq.bff.client.response.FileResponse;
import com.lynq.bff.controller.response.GlobalRestResponse;
import com.lynq.bff.service.Caller;
import com.lynq.bff.service.FileService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class FileControllerImplTest {

  private static final String USER_ID = "11111111-1111-1111-1111-111111111111";
  private static final String REQUEST_UUID = "018f9c3a-2b1d-7c4e-9a6f-1e2d3c4b5a99";
  private static final String FILE_ID = "0195f2c1-3b1a-7c2d-9f31-3f6a5f2c9d41";

  @Mock
  private FileService fileService;

  private FileControllerImpl fileController;

  @BeforeEach
  void setUp() {
    fileController = new FileControllerImpl(fileService);
  }

  @Test
  void answersARegisteredUploadWithCreated() {
    CreateFileUploadRequest request =
        CreateFileUploadRequest.builder().fileName("cv.pdf").build();
    CreateFileUploadResponse upload =
        CreateFileUploadResponse.builder().fileId(FILE_ID).build();
    when(fileService.createUpload(eq(request), any())).thenReturn(upload);

    ResponseEntity<GlobalRestResponse<CreateFileUploadResponse>> response =
        fileController.createUpload(request, REQUEST_UUID, USER_ID);

    assertThat(response.getStatusCode(), is(HttpStatus.CREATED));
    assertThat(response.getBody().getData(), is(sameInstance(upload)));
  }

  /**
   * lynq-file-storage reads the caller from a header, not from a token, so the gateway names the
   * verified user and carries no Authorization of its own.
   */
  @Test
  void callsTheServiceAsTheVerifiedCallerWithoutRelayingAToken() {
    CreateFileUploadRequest request =
        CreateFileUploadRequest.builder().fileName("cv.pdf").build();

    fileController.createUpload(request, REQUEST_UUID, USER_ID);

    ArgumentCaptor<Caller> caller = ArgumentCaptor.forClass(Caller.class);
    verify(fileService).createUpload(eq(request), caller.capture());
    assertThat(caller.getValue().userId(), is(USER_ID));
    assertThat(caller.getValue().requestUuid(), is(REQUEST_UUID));
    assertThat(caller.getValue().authorization(), is(nullValue()));
  }

  @Test
  void answersAConfirmedUploadWithOk() {
    FileResponse file = FileResponse.builder().fileId(FILE_ID).build();
    when(fileService.confirmUpload(eq(FILE_ID), any())).thenReturn(file);

    ResponseEntity<GlobalRestResponse<FileResponse>> response =
        fileController.confirmUpload(FILE_ID, REQUEST_UUID, USER_ID);

    assertThat(response.getStatusCode(), is(HttpStatus.OK));
    assertThat(response.getBody().getData(), is(sameInstance(file)));
  }

  @Test
  void answersAnOwnedFileWithOk() {
    FileResponse file = FileResponse.builder().fileId(FILE_ID).build();
    when(fileService.findOwnedFile(eq(FILE_ID), any())).thenReturn(file);

    ResponseEntity<GlobalRestResponse<FileResponse>> response =
        fileController.findOwnedFile(FILE_ID, REQUEST_UUID, USER_ID);

    assertThat(response.getStatusCode(), is(HttpStatus.OK));
    assertThat(response.getBody().getData(), is(sameInstance(file)));
  }

  @Test
  void answersTheDownloadUrlWithOk() {
    CreateFileDownloadResponse download =
        CreateFileDownloadResponse.builder().fileId(FILE_ID).build();
    when(fileService.createDownloadUrl(eq(FILE_ID), any())).thenReturn(download);

    ResponseEntity<GlobalRestResponse<CreateFileDownloadResponse>> response =
        fileController.createDownloadUrl(FILE_ID, REQUEST_UUID, USER_ID);

    assertThat(response.getStatusCode(), is(HttpStatus.OK));
    assertThat(response.getBody().getData(), is(sameInstance(download)));
  }

  @Test
  void answersTheBatchOfDownloadUrlsWithOk() {
    CreateFileDownloadBatchRequest request =
        CreateFileDownloadBatchRequest.builder().fileIds(List.of(FILE_ID)).build();
    Map<String, String> urls = Map.of(FILE_ID, "https://s3.local/get");
    when(fileService.createDownloadUrls(eq(request), any())).thenReturn(urls);

    ResponseEntity<GlobalRestResponse<Map<String, String>>> response =
        fileController.createDownloadUrls(request, REQUEST_UUID, USER_ID);

    assertThat(response.getStatusCode(), is(HttpStatus.OK));
    assertThat(response.getBody().getData(), is(sameInstance(urls)));
  }

  @Test
  void answersADeletedFileWithNoContentAndNoBody() {
    ResponseEntity<Void> response = fileController.deleteFile(FILE_ID, REQUEST_UUID, USER_ID);

    assertThat(response.getStatusCode(), is(HttpStatus.NO_CONTENT));
    assertThat(response.getBody(), is(nullValue()));
    verify(fileService).deleteFile(eq(FILE_ID), any());
  }
}
