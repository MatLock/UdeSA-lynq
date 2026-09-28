package com.lynq.bff.controller.impl;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.lynq.bff.client.request.LanguageDetectionRequest;
import com.lynq.bff.client.request.SkillEnhanceRequest;
import com.lynq.bff.client.request.TranslateResumeRequest;
import com.lynq.bff.client.response.LanguageDetectionResponse;
import com.lynq.bff.client.response.SkillEnhanceResponse;
import com.lynq.bff.client.response.SkillExtractionResponse;
import com.lynq.bff.controller.response.GlobalRestResponse;
import com.lynq.bff.exceptions.ForbiddenException;
import com.lynq.bff.security.LynqUserPrincipal;
import com.lynq.bff.service.Caller;
import com.lynq.bff.service.MlService;
import java.util.Map;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.mock.web.MockHttpServletRequest;

@ExtendWith(MockitoExtension.class)
class MlControllerImplTest {

  private static final String USER_ID = "11111111-1111-1111-1111-111111111111";
  private static final String REQUEST_UUID = "018f9c3a-2b1d-7c4e-9a6f-1e2d3c4b5a99";
  private static final String AUTHORIZATION = "Bearer access-token";

  private static final LynqUserPrincipal PRINCIPAL = new LynqUserPrincipal(
      USER_ID, "janedoe", "jane@lynq.com",
      List.of(new SimpleGrantedAuthority("R_CANDIDATE")), AUTHORIZATION);
  private static final Object RESUME = Map.of("fullName", "Jane Doe");

  @Mock
  private MlService mlService;

  private MlControllerImpl mlController;

  @BeforeEach
  void setUp() {
    mlController = new MlControllerImpl(mlService);
  }

  @Test
  void answersTheEnhancedSkillsWithOk() {
    SkillEnhanceRequest request = SkillEnhanceRequest.builder().title("Backend").build();
    SkillEnhanceResponse skills = SkillEnhanceResponse.builder().build();
    when(mlService.enhanceSkills(eq(request), any())).thenReturn(skills);

    ResponseEntity<GlobalRestResponse<SkillEnhanceResponse>> response =
        mlController.enhanceSkills(request, REQUEST_UUID, PRINCIPAL);

    assertThat(response.getStatusCode(), is(HttpStatus.OK));
    assertThat(response.getBody().getData(), is(sameInstance(skills)));
  }

  /** lynq-ml names its caller with a header, so the gateway relays no token to it. */
  @Test
  void callsTheServiceAsTheVerifiedCallerRelayingTheirToken() {
    SkillEnhanceRequest request = SkillEnhanceRequest.builder().title("Backend").build();

    mlController.enhanceSkills(request, REQUEST_UUID, PRINCIPAL);

    ArgumentCaptor<Caller> caller = ArgumentCaptor.forClass(Caller.class);
    verify(mlService).enhanceSkills(eq(request), caller.capture());
    assertThat(caller.getValue().userId(), is(USER_ID));
    assertThat(caller.getValue().requestUuid(), is(REQUEST_UUID));
    assertThat(caller.getValue().authorization(), is(AUTHORIZATION));
  }

  @Test
  void answersTheTranslatedResumeWithOk() {
    TranslateResumeRequest request =
        TranslateResumeRequest.builder().resume(RESUME).language("FR").build();
    Object translated = Map.of("fullName", "Jane Doe");
    when(mlService.translateResume(eq(request), any())).thenReturn(translated);

    ResponseEntity<GlobalRestResponse<Object>> response =
        mlController.translateResume(request, REQUEST_UUID, PRINCIPAL);

    assertThat(response.getStatusCode(), is(HttpStatus.OK));
    assertThat(response.getBody().getData(), is(sameInstance(translated)));
  }

  @Test
  void answersTheDetectedLanguageWithOk() {
    LanguageDetectionRequest request = LanguageDetectionRequest.builder().text("hola").build();
    LanguageDetectionResponse detected =
        LanguageDetectionResponse.builder().language("ES").build();
    when(mlService.detectLanguage(eq(request), any())).thenReturn(detected);

    ResponseEntity<GlobalRestResponse<LanguageDetectionResponse>> response =
        mlController.detectLanguage(request, REQUEST_UUID, PRINCIPAL);

    assertThat(response.getStatusCode(), is(HttpStatus.OK));
    assertThat(response.getBody().getData(), is(sameInstance(detected)));
  }

  @Test
  void answersTheExtractedResumeSkillsWithOk() {
    SkillExtractionResponse extracted = SkillExtractionResponse.builder().build();
    when(mlService.extractResumeSkills(eq(RESUME), eq("es"), any())).thenReturn(extracted);

    ResponseEntity<GlobalRestResponse<SkillExtractionResponse>> response =
        mlController.extractResumeSkills(RESUME, "es", REQUEST_UUID, PRINCIPAL);

    assertThat(response.getStatusCode(), is(HttpStatus.OK));
    assertThat(response.getBody().getData(), is(sameInstance(extracted)));
  }

  @Test
  void extractsTheResumeSkillsWithoutALanguageWhenTheCallerNamesNone() {
    mlController.extractResumeSkills(RESUME, null, REQUEST_UUID, PRINCIPAL);

    verify(mlService).extractResumeSkills(eq(RESUME), isNull(), any());
  }

  /**
   * These two are built from lynq-app-backend's data and are reached through its job endpoints.
   * They are mapped only so the refusal can name the route to use instead.
   */
  @ParameterizedTest
  @ValueSource(strings = {"/upskilling_suggestion", "/candidate-explanation"})
  void refusesTheEvaluationsLynqBackendOwnsAndSaysWhere(String path) {
    ForbiddenException thrown = assertThrows(ForbiddenException.class,
        () -> mlController.refuseUnrelayedEndpoint(requestTo(path)));

    assertThat(thrown.getMessage(), containsString("built from lynq-backend's data"));
    verifyNoInteractions(mlService);
  }

  /**
   * These fetch a caller-supplied URL server-side, which would make the gateway an SSRF vector.
   */
  @ParameterizedTest
  @ValueSource(strings = {"/parse-resume", "/resume-template-creation"})
  void refusesTheEndpointsThatFetchACallerSuppliedUrl(String path) {
    ForbiddenException thrown = assertThrows(ForbiddenException.class,
        () -> mlController.refuseUnrelayedEndpoint(requestTo(path)));

    assertThat(thrown.getMessage(), containsString("caller-supplied URL"));
    verifyNoInteractions(mlService);
  }

  private static MockHttpServletRequest requestTo(String path) {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
    request.setServletPath(path);
    return request;
  }
}
