package com.lynq.bff.service;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lynq.bff.client.LynqFileStorageClient;
import com.lynq.bff.client.LynqMlClient;
import com.lynq.bff.client.request.CreateFileUploadRequest;
import com.lynq.bff.client.request.ResumeTemplateCreationRequest;
import com.lynq.bff.client.response.CreateFileDownloadResponse;
import com.lynq.bff.client.response.CreateFileUploadResponse;
import com.lynq.bff.client.response.UserResponse;
import com.lynq.bff.controller.request.PreviewResumeRequest;
import com.lynq.bff.controller.response.GlobalRestResponse;
import com.lynq.bff.controller.response.ResumePreviewRestResponse;
import com.lynq.bff.enums.ResumeTemplate;
import com.lynq.bff.exceptions.BadGatewayException;
import com.lynq.bff.exceptions.BadRequestException;
import com.lynq.bff.exceptions.ForbiddenException;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ResumePreviewServiceTest {

  private static final String USER_ID = "11111111-1111-1111-1111-111111111111";
  private static final String REQUEST_UUID = "018f9c3a-2b1d-7c4e-9a6f-1e2d3c4b5a99";
  private static final String AUTHORIZATION = "Bearer access-token";
  private static final Caller CALLER = new Caller(USER_ID, REQUEST_UUID, AUTHORIZATION);

  private static final String FILE_ID = "0195f2c1-3b1a-7c2d-9f31-3f6a5f2c9d41";
  private static final String UPLOAD_URL = "https://lynq-bucket.s3/resume.pdf?X-Amz-Signature=put";
  private static final String PDF_URL = "https://lynq-bucket.s3/resume.pdf?X-Amz-Signature=get";
  private static final String AVATAR_URL = "https://lynq-bucket.s3/avatar.png?X-Amz-Signature=get";
  private static final Map<String, Object> RESUME = Map.of("summary", "Backend engineer");
  private static final String RESUME_FILE_NAME = "resume.pdf";
  private static final String PDF_CONTENT_TYPE = "application/pdf";

  private static final String ONLY_CANDIDATES = "Only users of type CANDIDATE can do this";
  private static final String RENDER_FAILED = "The resume PDF could not be rendered and stored";

  @Mock
  private CandidateReader candidateReader;

  @Mock
  private LynqFileStorageClient lynqFileStorageClient;

  @Mock
  private LynqMlClient lynqMlClient;

  private ResumePreviewService resumePreviewService;

  @BeforeEach
  void setUp() {
    resumePreviewService =
        new ResumePreviewService(candidateReader, lynqFileStorageClient, lynqMlClient);
  }

  @Test
  void previewReturnsTheStoredFileAndAReadUrlForIt() {
    givenCandidate(AVATAR_URL);
    givenRegisteredUpload();
    givenReadUrl();

    ResumePreviewRestResponse preview =
        resumePreviewService.preview(request(ResumeTemplate.CLASSIC), CALLER);

    assertThat(preview.getFileId(), is(FILE_ID));
    assertThat(preview.getPdfUrl(), is(PDF_URL));
  }

  @Test
  void previewRegistersThePdfUnderTheResumeFileName() {
    givenCandidate(AVATAR_URL);
    givenRegisteredUpload();
    givenReadUrl();

    resumePreviewService.preview(request(ResumeTemplate.MODERN), CALLER);

    ArgumentCaptor<CreateFileUploadRequest> captor =
        ArgumentCaptor.forClass(CreateFileUploadRequest.class);
    verify(lynqFileStorageClient).createUpload(captor.capture(), eq(REQUEST_UUID), eq(USER_ID));
    assertThat(captor.getValue().getFileName(), is(RESUME_FILE_NAME));
    assertThat(captor.getValue().getContentType(), is(PDF_CONTENT_TYPE));
  }

  @Test
  void previewSendsLynqMlTheSignedUploadUrlTheTemplateAndTheAvatar() {
    givenCandidate(AVATAR_URL);
    givenRegisteredUpload();
    givenReadUrl();

    resumePreviewService.preview(request(ResumeTemplate.CLASSIC), CALLER);

    ArgumentCaptor<ResumeTemplateCreationRequest> captor =
        ArgumentCaptor.forClass(ResumeTemplateCreationRequest.class);
    verify(lynqMlClient).createResumeTemplate(captor.capture(), eq(REQUEST_UUID), eq(USER_ID));
    ResumeTemplateCreationRequest render = captor.getValue();
    assertThat(render.getPutResumeUrl(), is(UPLOAD_URL));
    assertThat(render.getProfileUrl(), is(AVATAR_URL));
    assertThat(render.getTemplate(), is(ResumeTemplate.CLASSIC));
    assertThat(render.getResumeContent(), is(RESUME));
  }

  @Test
  void previewRendersWithoutAnAvatarWhenTheCandidateHasNoProfileImage() {
    givenCandidate(null);
    givenRegisteredUpload();
    givenReadUrl();

    resumePreviewService.preview(request(ResumeTemplate.MODERN), CALLER);

    ArgumentCaptor<ResumeTemplateCreationRequest> captor =
        ArgumentCaptor.forClass(ResumeTemplateCreationRequest.class);
    verify(lynqMlClient).createResumeTemplate(captor.capture(), eq(REQUEST_UUID), eq(USER_ID));
    assertThat(captor.getValue().getProfileUrl(), is((String) null));
  }

  @Test
  void previewSignsTheReadUrlOnlyAfterTheUploadIsConfirmed() {
    givenCandidate(AVATAR_URL);
    givenRegisteredUpload();
    givenReadUrl();

    resumePreviewService.preview(request(ResumeTemplate.MODERN), CALLER);

    InOrder order = inOrder(lynqFileStorageClient, lynqMlClient);
    order.verify(lynqFileStorageClient).createUpload(any(), eq(REQUEST_UUID), eq(USER_ID));
    order.verify(lynqMlClient).createResumeTemplate(any(), eq(REQUEST_UUID), eq(USER_ID));
    order.verify(lynqFileStorageClient).confirmUpload(FILE_ID, REQUEST_UUID, USER_ID);
    order.verify(lynqFileStorageClient).createDownloadUrl(FILE_ID, REQUEST_UUID);
  }

  @Test
  void previewDeletesTheRegisteredFileWhenTheRenderFails() {
    givenCandidate(AVATAR_URL);
    givenRegisteredUpload();
    doThrow(new IllegalStateException("render exploded"))
        .when(lynqMlClient).createResumeTemplate(any(), eq(REQUEST_UUID), eq(USER_ID));

    BadGatewayException exception = assertThrows(BadGatewayException.class,
        () -> resumePreviewService.preview(request(ResumeTemplate.MODERN), CALLER));

    assertThat(exception.getMessage(), is(RENDER_FAILED));
    verify(lynqFileStorageClient).deleteFile(FILE_ID, REQUEST_UUID, USER_ID);
    verify(lynqFileStorageClient, never()).createDownloadUrl(any(), any());
  }

  @Test
  void previewDeletesTheRegisteredFileWhenConfirmingTheUploadFails() {
    givenCandidate(AVATAR_URL);
    givenRegisteredUpload();
    doThrow(new IllegalStateException("still not in the bucket"))
        .when(lynqFileStorageClient).confirmUpload(FILE_ID, REQUEST_UUID, USER_ID);

    assertThrows(BadGatewayException.class,
        () -> resumePreviewService.preview(request(ResumeTemplate.MODERN), CALLER));

    verify(lynqFileStorageClient).deleteFile(FILE_ID, REQUEST_UUID, USER_ID);
  }

  @Test
  void previewReportsTheRenderFailureEvenWhenTheRollbackAlsoFails() {
    givenCandidate(AVATAR_URL);
    givenRegisteredUpload();
    doThrow(new IllegalStateException("render exploded"))
        .when(lynqMlClient).createResumeTemplate(any(), eq(REQUEST_UUID), eq(USER_ID));
    doThrow(new IllegalStateException("delete exploded"))
        .when(lynqFileStorageClient).deleteFile(FILE_ID, REQUEST_UUID, USER_ID);

    BadGatewayException exception = assertThrows(BadGatewayException.class,
        () -> resumePreviewService.preview(request(ResumeTemplate.MODERN), CALLER));

    assertThat(exception.getMessage(), is(RENDER_FAILED));
  }

  @Test
  void previewDoesNotTouchAnyServiceWhenTheCallerIsNotACandidate() {
    when(candidateReader.read(CALLER)).thenThrow(new ForbiddenException(ONLY_CANDIDATES));

    ForbiddenException exception = assertThrows(ForbiddenException.class,
        () -> resumePreviewService.preview(request(ResumeTemplate.MODERN), CALLER));

    assertThat(exception.getMessage(), is(ONLY_CANDIDATES));
    verify(lynqFileStorageClient, never()).createUpload(any(), any(), any());
    verify(lynqMlClient, never()).createResumeTemplate(any(), any(), any());
  }

  @Test
  void previewRejectsADraftWithoutAResume() {
    PreviewResumeRequest request = new PreviewResumeRequest(null, ResumeTemplate.MODERN);

    assertThrows(BadRequestException.class, () -> resumePreviewService.preview(request, CALLER));

    verify(candidateReader, never()).read(any());
  }

  @Test
  void previewRejectsADraftWithoutATemplate() {
    PreviewResumeRequest request = new PreviewResumeRequest(RESUME, null);

    assertThrows(BadRequestException.class, () -> resumePreviewService.preview(request, CALLER));

    verify(candidateReader, never()).read(any());
  }

  @Test
  void previewReportsABadGatewayWhenTheFileCannotBeRegistered() {
    givenCandidate(AVATAR_URL);
    when(lynqFileStorageClient.createUpload(any(), eq(REQUEST_UUID), eq(USER_ID)))
        .thenThrow(new IllegalStateException("file storage down"));

    assertThrows(BadGatewayException.class,
        () -> resumePreviewService.preview(request(ResumeTemplate.MODERN), CALLER));

    verify(lynqMlClient, never()).createResumeTemplate(any(), any(), any());
  }

  @Test
  void discardDeletesTheFileAsTheVerifiedCaller() {
    resumePreviewService.discard(FILE_ID, CALLER);

    verify(lynqFileStorageClient).deleteFile(FILE_ID, REQUEST_UUID, USER_ID);
  }

  @Test
  void discardReportsABadGatewayWhenTheFileCannotBeDeleted() {
    doThrow(new IllegalStateException("file storage down"))
        .when(lynqFileStorageClient).deleteFile(FILE_ID, REQUEST_UUID, USER_ID);

    assertThrows(BadGatewayException.class,
        () -> resumePreviewService.discard(FILE_ID, CALLER));
  }

  private PreviewResumeRequest request(ResumeTemplate template) {
    return new PreviewResumeRequest(RESUME, template);
  }

  private void givenCandidate(String profileImageUrl) {
    when(candidateReader.read(CALLER)).thenReturn(UserResponse.builder()
        .id(USER_ID)
        .userType("CANDIDATE")
        .userProfileImageUrl(profileImageUrl)
        .build());
  }

  private void givenRegisteredUpload() {
    when(lynqFileStorageClient.createUpload(any(), eq(REQUEST_UUID), eq(USER_ID)))
        .thenReturn(new GlobalRestResponse<>(true, CreateFileUploadResponse.builder()
            .fileId(FILE_ID)
            .uploadUrl(UPLOAD_URL)
            .build()));
  }

  private void givenReadUrl() {
    when(lynqFileStorageClient.createDownloadUrl(FILE_ID, REQUEST_UUID))
        .thenReturn(new GlobalRestResponse<>(true, CreateFileDownloadResponse.builder()
            .fileId(FILE_ID)
            .downloadUrl(PDF_URL)
            .build()));
  }
}
