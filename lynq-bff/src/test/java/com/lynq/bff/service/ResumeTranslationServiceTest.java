package com.lynq.bff.service;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lynq.bff.client.LynqBackendClient;
import com.lynq.bff.client.LynqMlClient;
import com.lynq.bff.client.request.TranslateResumeRequest;
import com.lynq.bff.client.response.SupportedLanguageResponse;
import com.lynq.bff.client.response.UserResponse;
import com.lynq.bff.client.response.UserResumeResponse;
import com.lynq.bff.controller.response.GlobalRestResponse;
import com.lynq.bff.exceptions.BadGatewayException;
import com.lynq.bff.exceptions.BadRequestException;
import com.lynq.bff.exceptions.ForbiddenException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ResumeTranslationServiceTest {

  private static final String USER_ID = "11111111-1111-1111-1111-111111111111";
  private static final String REQUEST_UUID = "018f9c3a-2b1d-7c4e-9a6f-1e2d3c4b5a99";
  private static final String AUTHORIZATION = "Bearer access-token";
  private static final Caller CALLER = new Caller(USER_ID, REQUEST_UUID, AUTHORIZATION);

  private static final String RESUME_ID = "resume-1";
  private static final String RESUME_NAME = "Jane Doe - Backend";
  private static final String SOURCE_LANGUAGE = "EN";
  private static final String TARGET_LANGUAGE = "FR";

  private static final Map<String, Object> SOURCE_RESUME = Map.of("summary", "Backend engineer");
  private static final Map<String, Object> TRANSLATED_RESUME =
      Map.of("summary", "Ingénieur backend",
          "personal_info", Map.of("full_name", "Jane Doe"));

  private static final String ONLY_CANDIDATES = "Only users of type CANDIDATE can do this";
  private static final String TRANSLATE_FAILED = "The resume could not be translated";

  @Mock
  private CandidateReader candidateReader;

  @Mock
  private LynqBackendClient lynqBackendClient;

  @Mock
  private LynqMlClient lynqMlClient;

  private ResumeTranslationService resumeTranslationService;

  @BeforeEach
  void setUp() {
    resumeTranslationService =
        new ResumeTranslationService(candidateReader, lynqBackendClient, lynqMlClient);
  }

  @Test
  void translateReturnsTheTranslatedResumeJson() {
    givenHappyPath();

    Object translated = resumeTranslationService.translate(RESUME_ID, TARGET_LANGUAGE, CALLER);

    assertThat(translated, is(TRANSLATED_RESUME));
  }

  @Test
  void translateSendsLynqMlTheSourceResumeAndTheNormalizedLanguage() {
    givenHappyPath();

    resumeTranslationService.translate(RESUME_ID, " fr ", CALLER);

    ArgumentCaptor<TranslateResumeRequest> captor =
        ArgumentCaptor.forClass(TranslateResumeRequest.class);
    verify(lynqMlClient).translateResume(captor.capture(), eq(REQUEST_UUID), eq(USER_ID));
    assertThat(captor.getValue().getResume(), is(SOURCE_RESUME));
    assertThat(captor.getValue().getLanguage(), is(TARGET_LANGUAGE));
  }

  @Test
  void translateStoresNothing() {
    givenHappyPath();

    resumeTranslationService.translate(RESUME_ID, TARGET_LANGUAGE, CALLER);

    // The candidate previews and confirms the translation themselves; storing is
    // POST /user/resume's job, reached only after that confirmation.
    verify(lynqBackendClient, never()).createResume(any(), any(), any());
  }

  @Test
  void translateRejectsAMissingTargetLanguage() {
    assertThrows(BadRequestException.class,
        () -> resumeTranslationService.translate(RESUME_ID, " ", CALLER));

    verify(candidateReader, never()).read(any());
    verify(lynqMlClient, never()).translateResume(any(), any(), any());
  }

  @Test
  void translateRejectsAResumeTheCallerDoesNotHold() {
    givenCandidate();
    givenResumes(resume(RESUME_ID, RESUME_NAME, SOURCE_LANGUAGE));

    assertThrows(BadRequestException.class,
        () -> resumeTranslationService.translate("someone-elses", TARGET_LANGUAGE, CALLER));

    verify(lynqMlClient, never()).translateResume(any(), any(), any());
  }

  @Test
  void translateRejectsALanguageTheBackendDoesNotSupport() {
    givenCandidate();
    givenResumes(resume(RESUME_ID, RESUME_NAME, SOURCE_LANGUAGE));
    givenSupportedLanguages("EN", "FR");

    assertThrows(BadRequestException.class,
        () -> resumeTranslationService.translate(RESUME_ID, "DE", CALLER));

    verify(lynqMlClient, never()).translateResume(any(), any(), any());
  }

  @Test
  void translateRejectsALanguageTheCandidateAlreadyHoldsAResumeIn() {
    givenCandidate();
    givenResumes(
        resume(RESUME_ID, RESUME_NAME, SOURCE_LANGUAGE),
        resume("resume-2", RESUME_NAME, TARGET_LANGUAGE));
    givenSupportedLanguages("EN", "FR");

    assertThrows(BadRequestException.class,
        () -> resumeTranslationService.translate(RESUME_ID, TARGET_LANGUAGE, CALLER));

    verify(lynqMlClient, never()).translateResume(any(), any(), any());
  }

  @Test
  void translateRejectsTheSourceResumesOwnLanguage() {
    givenCandidate();
    givenResumes(resume(RESUME_ID, RESUME_NAME, SOURCE_LANGUAGE));
    givenSupportedLanguages("EN", "FR");

    assertThrows(BadRequestException.class,
        () -> resumeTranslationService.translate(RESUME_ID, "en", CALLER));

    verify(lynqMlClient, never()).translateResume(any(), any(), any());
  }

  @Test
  void translateDoesNotTouchAnyServiceWhenTheCallerIsNotACandidate() {
    when(candidateReader.read(CALLER)).thenThrow(new ForbiddenException(ONLY_CANDIDATES));

    ForbiddenException exception = assertThrows(ForbiddenException.class,
        () -> resumeTranslationService.translate(RESUME_ID, TARGET_LANGUAGE, CALLER));

    assertThat(exception.getMessage(), is(ONLY_CANDIDATES));
    verify(lynqMlClient, never()).translateResume(any(), any(), any());
  }

  @Test
  void translateAnswersBadGatewayWhenTheTranslationFails() {
    givenCandidate();
    givenResumes(resume(RESUME_ID, RESUME_NAME, SOURCE_LANGUAGE));
    givenSupportedLanguages("EN", "FR");
    when(lynqMlClient.translateResume(any(), eq(REQUEST_UUID), eq(USER_ID)))
        .thenThrow(new IllegalStateException("llm exploded"));

    BadGatewayException exception = assertThrows(BadGatewayException.class,
        () -> resumeTranslationService.translate(RESUME_ID, TARGET_LANGUAGE, CALLER));

    assertThat(exception.getMessage(), is(TRANSLATE_FAILED));
  }

  private void givenHappyPath() {
    givenCandidate();
    givenResumes(resume(RESUME_ID, RESUME_NAME, SOURCE_LANGUAGE));
    givenSupportedLanguages("EN", "ES", "FR", "PR");
    givenTranslation();
  }

  private void givenCandidate() {
    when(candidateReader.read(CALLER)).thenReturn(UserResponse.builder()
        .id(USER_ID)
        .userType("CANDIDATE")
        .build());
  }

  private void givenResumes(UserResumeResponse... resumes) {
    when(lynqBackendClient.getUserResumes(REQUEST_UUID, AUTHORIZATION))
        .thenReturn(new GlobalRestResponse<>(true, List.of(resumes)));
  }

  private void givenSupportedLanguages(String... codes) {
    List<SupportedLanguageResponse> languages = java.util.Arrays.stream(codes)
        .map(code -> SupportedLanguageResponse.builder().code(code).name(code).build())
        .toList();
    lenient().when(lynqBackendClient.getSupportedResumeLanguages(REQUEST_UUID, AUTHORIZATION))
        .thenReturn(new GlobalRestResponse<>(true, languages));
  }

  private void givenTranslation() {
    lenient().when(lynqMlClient.translateResume(any(), eq(REQUEST_UUID), eq(USER_ID)))
        .thenReturn(new GlobalRestResponse<>(true, TRANSLATED_RESUME));
  }

  private static UserResumeResponse resume(String id, String name, String language) {
    return UserResumeResponse.builder()
        .id(id)
        .name(name)
        .language(language)
        .resume(SOURCE_RESUME)
        .build();
  }
}
