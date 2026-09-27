package com.lynq.bff.service;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lynq.bff.client.LynqFileStorageClient;
import com.lynq.bff.client.request.CreateFileDownloadBatchRequest;
import com.lynq.bff.client.request.CreateFileUploadRequest;
import com.lynq.bff.client.response.CreateFileDownloadResponse;
import com.lynq.bff.client.response.CreateFileUploadResponse;
import com.lynq.bff.client.response.FileResponse;
import com.lynq.bff.controller.response.GlobalRestResponse;
import com.lynq.bff.exceptions.BadGatewayException;
import com.lynq.bff.exceptions.ForbiddenException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FileServiceTest {

  private static final String USER_ID = "11111111-1111-1111-1111-111111111111";
  private static final String REQUEST_UUID = "018f9c3a-2b1d-7c4e-9a6f-1e2d3c4b5a99";
  private static final Caller CALLER = new Caller(USER_ID, REQUEST_UUID, null);

  private static final String FILE_ID = "0195f2c1-3b1a-7c2d-9f31-3f6a5f2c9d41";

  @Mock
  private LynqFileStorageClient lynqFileStorageClient;

  private FileService fileService;

  @BeforeEach
  void setUp() {
    fileService = new FileService(lynqFileStorageClient);
  }

  /**
   * lynq-file-storage reads the caller from the {@code user-id} header, not from a token, so the
   * verified id is what has to reach it.
   */
  @Test
  void registersTheUploadAsTheVerifiedCaller() {
    CreateFileUploadRequest request =
        CreateFileUploadRequest.builder().fileName("cv.pdf").build();
    CreateFileUploadResponse upload =
        CreateFileUploadResponse.builder().fileId(FILE_ID).build();
    when(lynqFileStorageClient.createUpload(request, REQUEST_UUID, USER_ID))
        .thenReturn(new GlobalRestResponse<>(true, upload));

    assertThat(fileService.createUpload(request, CALLER), is(sameInstance(upload)));
  }

  @Test
  void confirmsTheUploadAndAnswersTheStoredFile() {
    FileResponse file = FileResponse.builder().fileId(FILE_ID).build();
    when(lynqFileStorageClient.confirmUpload(FILE_ID, REQUEST_UUID, USER_ID))
        .thenReturn(new GlobalRestResponse<>(true, file));

    assertThat(fileService.confirmUpload(FILE_ID, CALLER), is(sameInstance(file)));
  }

  @Test
  void readsAFileTheCallerOwns() {
    FileResponse file = FileResponse.builder().fileId(FILE_ID).build();
    when(lynqFileStorageClient.findOwnedFile(FILE_ID, REQUEST_UUID, USER_ID))
        .thenReturn(new GlobalRestResponse<>(true, file));

    assertThat(fileService.findOwnedFile(FILE_ID, CALLER), is(sameInstance(file)));
  }

  /** The download url is not scoped to an owner downstream, so no caller id is sent with it. */
  @Test
  void issuesTheDownloadUrlWithoutNamingACaller() {
    CreateFileDownloadResponse download =
        CreateFileDownloadResponse.builder().fileId(FILE_ID).build();
    when(lynqFileStorageClient.createDownloadUrl(FILE_ID, REQUEST_UUID))
        .thenReturn(new GlobalRestResponse<>(true, download));

    assertThat(fileService.createDownloadUrl(FILE_ID, CALLER), is(sameInstance(download)));
  }

  @Test
  void issuesTheDownloadUrlsOfABatch() {
    CreateFileDownloadBatchRequest request =
        CreateFileDownloadBatchRequest.builder().fileIds(List.of(FILE_ID)).build();
    Map<String, String> urls = Map.of(FILE_ID, "https://s3.local/get");
    when(lynqFileStorageClient.createDownloadUrls(request, REQUEST_UUID))
        .thenReturn(new GlobalRestResponse<>(true, urls));

    assertThat(fileService.createDownloadUrls(request, CALLER), is(sameInstance(urls)));
  }

  @Test
  void deletesAFile() {
    fileService.deleteFile(FILE_ID, CALLER);

    verify(lynqFileStorageClient).deleteFile(FILE_ID, REQUEST_UUID, USER_ID);
  }

  @Test
  void keepsTheForbiddenWhenTheFileBelongsToAnotherUser() {
    doThrow(FeignErrors.status(403, """
        {"success": false, "reason": "The file belongs to another user"}"""))
        .when(lynqFileStorageClient).deleteFile(FILE_ID, REQUEST_UUID, USER_ID);

    ForbiddenException thrown =
        assertThrows(ForbiddenException.class, () -> fileService.deleteFile(FILE_ID, CALLER));

    assertThat(thrown.getMessage(), is("The file belongs to another user"));
  }

  @Test
  void answersBadGatewayWhenLynqFileStorageCannotBeReached() {
    when(lynqFileStorageClient.createDownloadUrl(FILE_ID, REQUEST_UUID))
        .thenThrow(FeignErrors.unreachable());

    BadGatewayException thrown = assertThrows(BadGatewayException.class,
        () -> fileService.createDownloadUrl(FILE_ID, CALLER));

    assertThat(thrown.getMessage(), is("The download url could not be issued"));
  }
}
