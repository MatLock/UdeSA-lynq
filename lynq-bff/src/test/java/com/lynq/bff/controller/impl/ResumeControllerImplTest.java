package com.lynq.bff.controller.impl;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lynq.bff.controller.request.PreviewResumeRequest;
import com.lynq.bff.controller.request.TranslateResumeRestRequest;
import com.lynq.bff.controller.request.UpdateResumeAliasRestRequest;
import com.lynq.bff.controller.response.GlobalRestResponse;
import com.lynq.bff.controller.response.ResumePreviewRestResponse;
import com.lynq.bff.enums.ResumeTemplate;
import com.lynq.bff.service.Caller;
import com.lynq.bff.service.ResumeAliasService;
import com.lynq.bff.service.ResumeDeletionService;
import com.lynq.bff.service.ResumeImportService;
import com.lynq.bff.service.ResumeTranslationService;
import com.lynq.bff.service.ResumePreviewService;
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
class ResumeControllerImplTest {

  private static final String USER_ID = "11111111-1111-1111-1111-111111111111";
  private static final String REQUEST_UUID = "018f9c3a-2b1d-7c4e-9a6f-1e2d3c4b5a99";
  private static final String AUTHORIZATION = "Bearer access-token";
  private static final String FILE_ID = "0195f2c1-3b1a-7c2d-9f31-3f6a5f2c9d41";
  private static final String UI_LANGUAGE = "es";
  private static final String RESUME_ID = "018f9c3a-2b1d-7c4e-9a6f-1e2d3c4b5a60";

  @Mock
  private ResumePreviewService resumePreviewService;

  @Mock
  private ResumeImportService resumeImportService;

  @Mock
  private ResumeDeletionService resumeDeletionService;

  @Mock
  private ResumeTranslationService resumeTranslationService;

  @Mock
  private ResumeAliasService resumeAliasService;

  private ResumeControllerImpl resumeController;

  @BeforeEach
  void setUp() {
    resumeController = new ResumeControllerImpl(
        resumePreviewService, resumeImportService, resumeDeletionService,
        resumeTranslationService, resumeAliasService);
  }

  @Test
  void updateResumeAliasRespondsWithOkAndTheUpdatedResume() {
    UpdateResumeAliasRestRequest request = new UpdateResumeAliasRestRequest("Backend roles");
    Object updated = Map.of("id", RESUME_ID, "alias", "Backend roles");
    when(resumeAliasService.assign(RESUME_ID, "Backend roles",
        new Caller(USER_ID, REQUEST_UUID, AUTHORIZATION))).thenReturn(updated);

    ResponseEntity<GlobalRestResponse<Object>> response = resumeController.updateResumeAlias(
        RESUME_ID, request, REQUEST_UUID, AUTHORIZATION, USER_ID);

    assertThat(response.getStatusCode(), is(HttpStatus.OK));
    assertThat(response.getBody(), is(notNullValue()));
    assertThat(response.getBody().isSuccess(), is(true));
    assertThat(response.getBody().getData(), is(updated));
  }

  @Test
  void updateResumeAliasDelegatesToTheServiceWithTheCaller() {
    UpdateResumeAliasRestRequest request = new UpdateResumeAliasRestRequest("Backend roles");

    resumeController.updateResumeAlias(RESUME_ID, request, REQUEST_UUID, AUTHORIZATION, USER_ID);

    ArgumentCaptor<Caller> caller = ArgumentCaptor.forClass(Caller.class);
    verify(resumeAliasService).assign(eq(RESUME_ID), eq("Backend roles"), caller.capture());
    assertThat(caller.getValue().userId(), is(USER_ID));
    assertThat(caller.getValue().requestUuid(), is(REQUEST_UUID));
    // The alias lives in the app-backend, which authenticates by bearer token.
    assertThat(caller.getValue().authorization(), is(AUTHORIZATION));
  }

  @Test
  void translateResumeRespondsWithOkAndTheTranslatedJson() {
    TranslateResumeRestRequest request = new TranslateResumeRestRequest("FR");
    Object translated = Map.of("summary", "Ingénieur backend");
    when(resumeTranslationService.translate("resume-1", "FR",
        new Caller(USER_ID, REQUEST_UUID, AUTHORIZATION))).thenReturn(translated);

    ResponseEntity<GlobalRestResponse<Object>> response = resumeController.translateResume(
        "resume-1", request, REQUEST_UUID, AUTHORIZATION, USER_ID);

    // Nothing is created here: the translated JSON goes back for the preview step.
    assertThat(response.getStatusCode(), is(HttpStatus.OK));
    assertThat(response.getBody(), is(notNullValue()));
    assertThat(response.getBody().isSuccess(), is(true));
    assertThat(response.getBody().getData(), is(translated));
  }

  @Test
  void translateResumeDelegatesToTheServiceWithTheCaller() {
    TranslateResumeRestRequest request = new TranslateResumeRestRequest("FR");

    resumeController.translateResume("resume-1", request, REQUEST_UUID, AUTHORIZATION, USER_ID);

    verify(resumeTranslationService).translate("resume-1", "FR",
        new Caller(USER_ID, REQUEST_UUID, AUTHORIZATION));
  }

  @Test
  void deleteResumeRespondsWithNoContent() {
    ResponseEntity<Void> response =
        resumeController.deleteResume(RESUME_ID, REQUEST_UUID, AUTHORIZATION, USER_ID);

    assertThat(response.getStatusCode(), is(HttpStatus.NO_CONTENT));
  }

  @Test
  void deleteResumeDelegatesToTheServiceWithTheCaller() {
    resumeController.deleteResume(RESUME_ID, REQUEST_UUID, AUTHORIZATION, USER_ID);

    ArgumentCaptor<Caller> caller = ArgumentCaptor.forClass(Caller.class);
    verify(resumeDeletionService).delete(eq(RESUME_ID), caller.capture());
    assertThat(caller.getValue().userId(), is(USER_ID));
    assertThat(caller.getValue().requestUuid(), is(REQUEST_UUID));
    // Unlike the preview discard, deleting a saved resume goes through the
    // app-backend, which authenticates by bearer token.
    assertThat(caller.getValue().authorization(), is(AUTHORIZATION));
  }

  @Test
  void previewResumeRespondsWithCreatedAndTheEnvelopedPreview() {
    PreviewResumeRequest request = request();
    ResumePreviewRestResponse preview = ResumePreviewRestResponse.builder()
        .fileId(FILE_ID)
        .build();
    when(resumePreviewService.preview(eq(request), any()))
        .thenReturn(preview);

    ResponseEntity<GlobalRestResponse<ResumePreviewRestResponse>> response =
        resumeController.previewResume(request, REQUEST_UUID, AUTHORIZATION, USER_ID);

    assertThat(response.getStatusCode(), is(HttpStatus.CREATED));
    GlobalRestResponse<ResumePreviewRestResponse> body = response.getBody();
    assertThat(body, is(notNullValue()));
    assertThat(body.isSuccess(), is(true));
    assertThat(body.getData(), is(sameInstance(preview)));
  }

  @Test
  void previewResumeCallsTheFlowAsTheVerifiedCallerNotTheRequestBody() {
    PreviewResumeRequest request = request();
    when(resumePreviewService.preview(eq(request), any()))
        .thenReturn(ResumePreviewRestResponse.builder().build());

    resumeController.previewResume(request, REQUEST_UUID, AUTHORIZATION, USER_ID);

    ArgumentCaptor<Caller> captor = ArgumentCaptor.forClass(Caller.class);
    verify(resumePreviewService).preview(eq(request), captor.capture());
    Caller caller = captor.getValue();
    assertThat(caller.userId(), is(USER_ID));
    assertThat(caller.requestUuid(), is(REQUEST_UUID));
    assertThat(caller.authorization(), is(AUTHORIZATION));
  }

  @Test
  void discardResumePreviewRespondsWithNoContent() {
    ResponseEntity<Void> response =
        resumeController.discardResumePreview(FILE_ID, REQUEST_UUID, USER_ID);

    assertThat(response.getStatusCode(), is(HttpStatus.NO_CONTENT));
  }

  @Test
  void discardResumePreviewCallsTheFlowWithTheVerifiedCaller() {
    resumeController.discardResumePreview(FILE_ID, REQUEST_UUID, USER_ID);

    ArgumentCaptor<Caller> captor = ArgumentCaptor.forClass(Caller.class);
    verify(resumePreviewService).discard(eq(FILE_ID), captor.capture());
    Caller caller = captor.getValue();
    assertThat(caller.userId(), is(USER_ID));
    assertThat(caller.requestUuid(), is(REQUEST_UUID));
  }

  @Test
  void importResumeDocumentRespondsWithCreatedAndTheEnvelopedResume() {
    Object stored = Map.of("id", "resume-1");
    when(resumeImportService.importUploadedDocument(eq(FILE_ID), eq(UI_LANGUAGE), any()))
        .thenReturn(stored);

    ResponseEntity<GlobalRestResponse<Object>> response = resumeController.importResumeDocument(
        FILE_ID, UI_LANGUAGE, REQUEST_UUID, AUTHORIZATION, USER_ID);

    assertThat(response.getStatusCode(), is(HttpStatus.CREATED));
    GlobalRestResponse<Object> body = response.getBody();
    assertThat(body, is(notNullValue()));
    assertThat(body.isSuccess(), is(true));
    assertThat(body.getData(), is(sameInstance(stored)));
  }

  @Test
  void importResumeDocumentCallsTheFlowAsTheVerifiedCaller() {
    when(resumeImportService.importUploadedDocument(eq(FILE_ID), eq(UI_LANGUAGE), any()))
        .thenReturn(Map.of());

    resumeController.importResumeDocument(
        FILE_ID, UI_LANGUAGE, REQUEST_UUID, AUTHORIZATION, USER_ID);

    ArgumentCaptor<Caller> captor = ArgumentCaptor.forClass(Caller.class);
    verify(resumeImportService).importUploadedDocument(eq(FILE_ID), eq(UI_LANGUAGE), captor.capture());
    Caller caller = captor.getValue();
    assertThat(caller.userId(), is(USER_ID));
    assertThat(caller.requestUuid(), is(REQUEST_UUID));
    assertThat(caller.authorization(), is(AUTHORIZATION));
  }

  private PreviewResumeRequest request() {
    return new PreviewResumeRequest(Map.of("summary", "Backend engineer"), ResumeTemplate.MODERN);
  }
}
