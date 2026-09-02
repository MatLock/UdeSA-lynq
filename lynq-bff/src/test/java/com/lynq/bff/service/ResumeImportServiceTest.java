package com.lynq.bff.service;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lynq.bff.client.LynqBackendClient;
import com.lynq.bff.client.LynqFileStorageClient;
import com.lynq.bff.client.LynqMlClient;
import com.lynq.bff.client.request.CreateResumeRequest;
import com.lynq.bff.client.request.LanguageDetectionRequest;
import com.lynq.bff.client.request.ParseResumeRequest;
import com.lynq.bff.client.response.CreateFileDownloadResponse;
import com.lynq.bff.client.response.LanguageDetectionResponse;
import com.lynq.bff.client.response.UserResponse;
import com.lynq.bff.controller.response.GlobalRestResponse;
import com.lynq.bff.exceptions.BadGatewayException;
import com.lynq.bff.exceptions.ForbiddenException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ResumeImportServiceTest {

  private static final String USER_ID = "11111111-1111-1111-1111-111111111111";
  private static final String REQUEST_UUID = "018f9c3a-2b1d-7c4e-9a6f-1e2d3c4b5a99";
  private static final String AUTHORIZATION = "Bearer access-token";
  private static final Caller CALLER = new Caller(USER_ID, REQUEST_UUID, AUTHORIZATION);

  private static final String FILE_ID = "0195f2c1-3b1a-7c2d-9f31-3f6a5f2c9d41";
  private static final String DOCUMENT_URL = "https://lynq-bucket.s3/cv.pdf?X-Amz-Signature=get";
  private static final String UI_LANGUAGE = "es";
  private static final String FULL_NAME = "Jane Doe";
  private static final String SUMMARY = "Backend engineer with six years of experience.";
  private static final String ROLE_DESCRIPTION = "Built and scaled payment services.";
  private static final Object PARSED_RESUME = Map.of(
      "personal_info", Map.of("full_name", FULL_NAME, "headline", "Backend Engineer"),
      "summary", SUMMARY,
      "work_experience", List.of(Map.of("company", "LYNQ", "description", ROLE_DESCRIPTION)),
      "skills", Map.of("technical", List.of("Java")));
  private static final Object STORED_RESUME = Map.of("id", "resume-1", "language", "EN");

  private static final String ONLY_CANDIDATES = "Only users of type CANDIDATE can do this";
  private static final String IMPORT_FAILED = "The uploaded resume could not be imported";
  private static final String CONFIRM_FAILED =
      "The uploaded resume document could not be confirmed";

  @Mock
  private CandidateReader candidateReader;

  @Mock
  private LynqBackendClient lynqBackendClient;

  @Mock
  private LynqFileStorageClient lynqFileStorageClient;

  @Mock
  private LynqMlClient lynqMlClient;

  private ResumeImportService resumeImportService;

  @BeforeEach
  void setUp() {
    resumeImportService = new ResumeImportService(
        candidateReader, lynqBackendClient, lynqFileStorageClient, lynqMlClient);
  }

  @Test
  void importReturnsTheResumeLynqAppBackendStored() {
    givenCandidate();
    givenReadUrl();
    givenParsedResume();
    givenDetectedLanguage("EN");
    givenStoredResume();

    Object stored = resumeImportService.importUploadedDocument(FILE_ID, UI_LANGUAGE, CALLER);

    assertThat(stored, is(sameInstance(STORED_RESUME)));
  }

  @Test
  void importRunsTheDocumentThroughEveryServiceInOrder() {
    givenCandidate();
    givenReadUrl();
    givenParsedResume();
    givenDetectedLanguage("EN");
    givenStoredResume();

    resumeImportService.importUploadedDocument(FILE_ID, UI_LANGUAGE, CALLER);

    InOrder order = inOrder(lynqBackendClient, lynqFileStorageClient, lynqMlClient);
    order.verify(lynqBackendClient).confirmResumeUpload(FILE_ID, REQUEST_UUID, AUTHORIZATION);
    order.verify(lynqFileStorageClient).createDownloadUrl(FILE_ID, REQUEST_UUID);
    order.verify(lynqMlClient).parseResume(any(), eq(REQUEST_UUID), eq(USER_ID));
    order.verify(lynqMlClient).detectLanguage(any(), eq(REQUEST_UUID), eq(USER_ID));
    order.verify(lynqBackendClient).createResume(any(), eq(REQUEST_UUID), eq(AUTHORIZATION));
  }

  @Test
  void importSendsLynqMlTheSignedReadUrlOfTheUploadedDocument() {
    givenCandidate();
    givenReadUrl();
    givenParsedResume();
    givenDetectedLanguage("EN");
    givenStoredResume();

    resumeImportService.importUploadedDocument(FILE_ID, UI_LANGUAGE, CALLER);

    ArgumentCaptor<ParseResumeRequest> captor = ArgumentCaptor.forClass(ParseResumeRequest.class);
    verify(lynqMlClient).parseResume(captor.capture(), eq(REQUEST_UUID), eq(USER_ID));
    assertThat(captor.getValue().getPreSignedUrl(), is(DOCUMENT_URL));
  }

  @Test
  void importClassifiesTheLanguageFromTheResumesOwnProse() {
    givenCandidate();
    givenReadUrl();
    givenParsedResume();
    givenDetectedLanguage("EN");
    givenStoredResume();

    resumeImportService.importUploadedDocument(FILE_ID, UI_LANGUAGE, CALLER);

    ArgumentCaptor<LanguageDetectionRequest> captor =
        ArgumentCaptor.forClass(LanguageDetectionRequest.class);
    verify(lynqMlClient).detectLanguage(captor.capture(), eq(REQUEST_UUID), eq(USER_ID));
    String text = captor.getValue().getText();
    assertThat(text.contains(SUMMARY), is(true));
    assertThat(text.contains(ROLE_DESCRIPTION), is(true));

    assertThat(text.contains("Java"), is(false));
    assertThat(text.contains("LYNQ"), is(false));
  }

  @Test
  void importStoresTheParsedResumeUnderTheCandidatesNameAndTheDetectedLanguage() {
    givenCandidate();
    givenReadUrl();
    givenParsedResume();
    givenDetectedLanguage("ES");
    givenStoredResume();

    resumeImportService.importUploadedDocument(FILE_ID, UI_LANGUAGE, CALLER);

    ArgumentCaptor<CreateResumeRequest> captor = ArgumentCaptor.forClass(CreateResumeRequest.class);
    verify(lynqBackendClient).createResume(captor.capture(), eq(REQUEST_UUID), eq(AUTHORIZATION));
    CreateResumeRequest request = captor.getValue();
    assertThat(request.getName(), is(FULL_NAME));
    assertThat(request.getLanguage(), is("ES"));
    assertThat(request.getFileId(), is(FILE_ID));
    assertThat(request.getResume(), is(sameInstance(PARSED_RESUME)));
  }

  @Test
  void importFallsBackToTheCallersLanguageWhenTheResumeHasNoProseToClassify() {
    givenCandidate();
    givenReadUrl();
    when(lynqMlClient.parseResume(any(), eq(REQUEST_UUID), eq(USER_ID)))
        .thenReturn(new GlobalRestResponse<>(true, Map.of(
            "personal_info", Map.of("full_name", FULL_NAME),
            "skills", Map.of("technical", List.of("Java")))));
    givenStoredResume();

    resumeImportService.importUploadedDocument(FILE_ID, UI_LANGUAGE, CALLER);

    verify(lynqMlClient, never()).detectLanguage(any(), any(), any());
    ArgumentCaptor<CreateResumeRequest> captor = ArgumentCaptor.forClass(CreateResumeRequest.class);
    verify(lynqBackendClient).createResume(captor.capture(), eq(REQUEST_UUID), eq(AUTHORIZATION));
    assertThat(captor.getValue().getLanguage(), is("ES"));
  }

  @Test
  void importStoresEnglishWhenNeitherTheResumeNorTheCallerNamesAStoredLanguage() {
    givenCandidate();
    givenReadUrl();
    givenParsedResume();
    givenDetectedLanguage("Klingon");
    givenStoredResume();

    resumeImportService.importUploadedDocument(FILE_ID, "de", CALLER);

    ArgumentCaptor<CreateResumeRequest> captor = ArgumentCaptor.forClass(CreateResumeRequest.class);
    verify(lynqBackendClient).createResume(captor.capture(), eq(REQUEST_UUID), eq(AUTHORIZATION));
    assertThat(captor.getValue().getLanguage(), is("EN"));
  }

  @Test
  void importDeletesTheDocumentWhenItCannotBeParsed() {
    givenCandidate();
    givenReadUrl();
    when(lynqMlClient.parseResume(any(), eq(REQUEST_UUID), eq(USER_ID)))
        .thenThrow(new IllegalStateException("the LLM returned nonsense"));

    BadGatewayException exception = assertThrows(BadGatewayException.class,
        () -> resumeImportService.importUploadedDocument(FILE_ID, UI_LANGUAGE, CALLER));

    assertThat(exception.getMessage(), is(IMPORT_FAILED));
    verify(lynqFileStorageClient).deleteFile(FILE_ID, REQUEST_UUID, USER_ID);
    verify(lynqBackendClient, never()).createResume(any(), any(), any());
  }

  @Test
  void importDeletesTheDocumentWhenTheResumeCannotBeStored() {
    givenCandidate();
    givenReadUrl();
    givenParsedResume();
    givenDetectedLanguage("EN");
    when(lynqBackendClient.createResume(any(), eq(REQUEST_UUID), eq(AUTHORIZATION)))
        .thenThrow(new IllegalStateException("backend down"));

    assertThrows(BadGatewayException.class,
        () -> resumeImportService.importUploadedDocument(FILE_ID, UI_LANGUAGE, CALLER));

    verify(lynqFileStorageClient).deleteFile(FILE_ID, REQUEST_UUID, USER_ID);
  }

  @Test
  void importLeavesTheDocumentAloneWhenConfirmingItFails() {
    givenCandidate();
    doThrow(new IllegalStateException("the bytes never arrived"))
        .when(lynqBackendClient).confirmResumeUpload(FILE_ID, REQUEST_UUID, AUTHORIZATION);

    BadGatewayException exception = assertThrows(BadGatewayException.class,
        () -> resumeImportService.importUploadedDocument(FILE_ID, UI_LANGUAGE, CALLER));

    assertThat(exception.getMessage(), is(CONFIRM_FAILED));
    verify(lynqFileStorageClient, never()).deleteFile(any(), any(), any());
    verify(lynqMlClient, never()).parseResume(any(), any(), any());
  }

  @Test
  void importReportsTheFailureEvenWhenTheRollbackAlsoFails() {
    givenCandidate();
    givenReadUrl();
    when(lynqMlClient.parseResume(any(), eq(REQUEST_UUID), eq(USER_ID)))
        .thenThrow(new IllegalStateException("the LLM returned nonsense"));
    doThrow(new IllegalStateException("delete exploded"))
        .when(lynqFileStorageClient).deleteFile(FILE_ID, REQUEST_UUID, USER_ID);

    BadGatewayException exception = assertThrows(BadGatewayException.class,
        () -> resumeImportService.importUploadedDocument(FILE_ID, UI_LANGUAGE, CALLER));

    assertThat(exception.getMessage(), is(IMPORT_FAILED));
  }

  @Test
  void importTouchesNothingWhenTheCallerIsNotACandidate() {
    when(candidateReader.read(CALLER)).thenThrow(new ForbiddenException(ONLY_CANDIDATES));

    assertThrows(ForbiddenException.class,
        () -> resumeImportService.importUploadedDocument(FILE_ID, UI_LANGUAGE, CALLER));

    verify(lynqBackendClient, never()).confirmResumeUpload(any(), any(), any());
    verify(lynqMlClient, never()).parseResume(any(), any(), any());
  }

  private void givenCandidate() {
    when(candidateReader.read(CALLER))
        .thenReturn(UserResponse.builder().id(USER_ID).userType("CANDIDATE").build());
  }

  private void givenReadUrl() {
    when(lynqFileStorageClient.createDownloadUrl(FILE_ID, REQUEST_UUID))
        .thenReturn(new GlobalRestResponse<>(true, CreateFileDownloadResponse.builder()
            .fileId(FILE_ID)
            .downloadUrl(DOCUMENT_URL)
            .build()));
  }

  private void givenParsedResume() {
    when(lynqMlClient.parseResume(any(), eq(REQUEST_UUID), eq(USER_ID)))
        .thenReturn(new GlobalRestResponse<>(true, PARSED_RESUME));
  }

  private void givenDetectedLanguage(String language) {
    when(lynqMlClient.detectLanguage(any(), eq(REQUEST_UUID), eq(USER_ID)))
        .thenReturn(new GlobalRestResponse<>(true,
            LanguageDetectionResponse.builder().language(language).build()));
  }

  private void givenStoredResume() {
    when(lynqBackendClient.createResume(any(), eq(REQUEST_UUID), eq(AUTHORIZATION)))
        .thenReturn(new GlobalRestResponse<>(true, STORED_RESUME));
  }
}
