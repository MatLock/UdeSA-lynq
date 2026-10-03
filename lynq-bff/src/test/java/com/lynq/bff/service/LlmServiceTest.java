package com.lynq.bff.service;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.lynq.bff.client.LynqLlmClient;
import com.lynq.bff.client.request.LanguageDetectionRequest;
import com.lynq.bff.client.request.SkillEnhanceRequest;
import com.lynq.bff.client.request.TranslateResumeRequest;
import com.lynq.bff.client.response.LanguageDetectionResponse;
import com.lynq.bff.client.response.SkillEnhanceResponse;
import com.lynq.bff.client.response.SkillExtractionResponse;
import com.lynq.bff.controller.response.GlobalRestResponse;
import com.lynq.bff.enums.WorkType;
import com.lynq.bff.exceptions.BadGatewayException;
import com.lynq.bff.exceptions.BadRequestException;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LlmServiceTest {

  private static final String USER_ID = "11111111-1111-1111-1111-111111111111";
  private static final String REQUEST_UUID = "018f9c3a-2b1d-7c4e-9a6f-1e2d3c4b5a99";
  private static final String AUTHORIZATION = "Bearer access-token";
  private static final Caller CALLER = new Caller(USER_ID, REQUEST_UUID, AUTHORIZATION);

  private static final Object RESUME = Map.of("fullName", "Jane Doe");

  @Mock
  private LynqLlmClient lynqLlmClient;

  private LlmService llmService;

  @BeforeEach
  void setUp() {
    llmService = new LlmService(lynqLlmClient);
  }

  /** lynq-llm resolves the caller against lynq-iam, so the credential is what is relayed. */
  @Test
  void enhancesTheSkillsOfAJobPostAsTheVerifiedCaller() {
    SkillEnhanceRequest request = SkillEnhanceRequest.builder()
        .title("Backend Engineer")
        .workType(WorkType.REMOTE)
        .build();
    SkillEnhanceResponse skills = SkillEnhanceResponse.builder().build();
    when(lynqLlmClient.enhanceSkills(request, REQUEST_UUID, AUTHORIZATION))
        .thenReturn(new GlobalRestResponse<>(true, skills));

    assertThat(llmService.enhanceSkills(request, CALLER), is(sameInstance(skills)));
  }

  @Test
  void translatesAResume() {
    TranslateResumeRequest request =
        TranslateResumeRequest.builder().resume(RESUME).language("FR").build();
    Object translated = Map.of("fullName", "Jane Doe");
    when(lynqLlmClient.translateResume(request, REQUEST_UUID, AUTHORIZATION))
        .thenReturn(new GlobalRestResponse<>(true, translated));

    assertThat(llmService.translateResume(request, CALLER), is(sameInstance(translated)));
  }

  @Test
  void detectsTheLanguageOfAText() {
    LanguageDetectionRequest request = LanguageDetectionRequest.builder().text("hola").build();
    LanguageDetectionResponse detected =
        LanguageDetectionResponse.builder().language("ES").build();
    when(lynqLlmClient.detectLanguage(request, REQUEST_UUID, AUTHORIZATION))
        .thenReturn(new GlobalRestResponse<>(true, detected));

    assertThat(llmService.detectLanguage(request, CALLER), is(sameInstance(detected)));
  }

  @Test
  void extractsTheResumeSkillsInTheLanguageAsked() {
    SkillExtractionResponse extracted = SkillExtractionResponse.builder().build();
    when(lynqLlmClient.extractResumeSkills(RESUME, "es", REQUEST_UUID, AUTHORIZATION))
        .thenReturn(new GlobalRestResponse<>(true, extracted));

    assertThat(llmService.extractResumeSkills(RESUME, "es", CALLER), is(sameInstance(extracted)));
  }

  @Test
  void letsTheLanguageGoUnsetWhenTheCallerNamesNone() {
    SkillExtractionResponse extracted = SkillExtractionResponse.builder().build();
    when(lynqLlmClient.extractResumeSkills(RESUME, null, REQUEST_UUID, AUTHORIZATION))
        .thenReturn(new GlobalRestResponse<>(true, extracted));

    assertThat(llmService.extractResumeSkills(RESUME, null, CALLER), is(sameInstance(extracted)));
  }

  @Test
  void keepsTheBadRequestWhenLynqLlmRejectsThePayload() {
    LanguageDetectionRequest request = LanguageDetectionRequest.builder().build();
    when(lynqLlmClient.detectLanguage(request, REQUEST_UUID, AUTHORIZATION))
        .thenThrow(FeignErrors.status(400, """
            {"success": false, "reason": "text is required"}"""));

    BadRequestException thrown = assertThrows(BadRequestException.class,
        () -> llmService.detectLanguage(request, CALLER));

    assertThat(thrown.getMessage(), is("text is required"));
  }

  /**
   * lynq-llm answers 502 of its own when the model behind it fails. That is its failure, not an
   * answer for the caller, so it does not become a reason of theirs.
   */
  @Test
  void answersBadGatewayWhenTheModelBehindLynqLlmFails() {
    TranslateResumeRequest request =
        TranslateResumeRequest.builder().resume(RESUME).language("FR").build();
    when(lynqLlmClient.translateResume(request, REQUEST_UUID, AUTHORIZATION))
        .thenThrow(FeignErrors.status(502, """
            {"detail": "LLM request failed"}"""));

    BadGatewayException thrown = assertThrows(BadGatewayException.class,
        () -> llmService.translateResume(request, CALLER));

    assertThat(thrown.getMessage(), is("The resume could not be translated"));
  }
}
