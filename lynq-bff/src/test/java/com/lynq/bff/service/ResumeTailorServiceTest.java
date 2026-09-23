package com.lynq.bff.service;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynq.bff.client.LynqAgentClient;
import com.lynq.bff.client.LynqBackendClient;
import com.lynq.bff.client.request.ApplyJobRequest;
import com.lynq.bff.client.request.MarkConversationAppliedRequest;
import com.lynq.bff.client.request.StartTailorConversationRequest;
import com.lynq.bff.client.request.TailorTurnRequest;
import com.lynq.bff.client.response.JobDetailsResponse;
import com.lynq.bff.client.response.UserResumeResponse;
import com.lynq.bff.controller.response.GlobalRestResponse;
import com.lynq.bff.controller.response.ResumeTailorApplyRestResponse;
import com.lynq.bff.exceptions.BadGatewayException;
import com.lynq.bff.exceptions.BadRequestException;
import com.lynq.bff.exceptions.ConflictException;
import com.lynq.bff.exceptions.ForbiddenException;
import com.lynq.bff.exceptions.NotFoundException;
import feign.FeignException;
import feign.Request;
import feign.RequestTemplate;
import feign.Response;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ResumeTailorServiceTest {

  private static final String USER_ID = "11111111-1111-1111-1111-111111111111";
  private static final String REQUEST_UUID = "018f9c3a-2b1d-7c4e-9a6f-1e2d3c4b5a99";
  private static final String AUTHORIZATION = "Bearer access-token";
  private static final Caller CALLER = new Caller(USER_ID, REQUEST_UUID, AUTHORIZATION);

  private static final String RESUME_ID = "018f9c3a-2b1d-7c4e-9a6f-1e2d3c4b5a60";
  private static final String OTHER_RESUME_ID = "018f9c3a-2b1d-7c4e-9a6f-1e2d3c4b5aff";
  private static final String JOB_ID = "018f9c3a-2b1d-7c4e-9a6f-1e2d3c4b5a61";
  private static final String CONVERSATION_ID = "0195f2c1-3b1a-7c2d-9f31-3f6a5f2c9d42";
  private static final String TURN_KEY = "0195f2c1-3b1a-7c2d-9f31-3f6a5f2c9d43";

  private static final String RESUME_LANGUAGE = "EN";
  private static final Map<String, Object> BASE_RESUME =
      Map.of("summary", "Backend engineer", "skills", Map.of("technical", List.of("Java")));

  private static final String JOB_TITLE = "Senior Backend Engineer";
  private static final String JOB_DESCRIPTION = "Build and scale the hiring platform.";
  private static final String COMPANY_NAME = "Acme";
  private static final List<String> JOB_SKILLS = List.of("Java", "Kubernetes");

  private static final Map<String, Object> CONVERSATION = Map.of(
      "conversationId", CONVERSATION_ID,
      "jobId", JOB_ID,
      "baseResumeId", RESUME_ID,
      "status", "ACTIVE");

  private static final Map<String, Object> APPLICATION = Map.of(
      "applicationId", "018fa1b2-2b1d-7c4e-9a6f-1e2d3c4b5a62",
      "jobId", JOB_ID);

  @Mock
  private LynqBackendClient lynqBackendClient;

  @Mock
  private LynqAgentClient lynqAgentClient;

  private ResumeTailorService resumeTailorService;

  @BeforeEach
  void setUp() {
    resumeTailorService =
        new ResumeTailorService(lynqBackendClient, lynqAgentClient, new ObjectMapper());
  }

  @Test
  void startSendsTheAgentTheJobTheGatewayReadAndTheCallersOwnResume() {
    givenTheCallersResumes();
    givenTheJob();
    when(lynqAgentClient.startConversation(any(), eq(REQUEST_UUID), eq(USER_ID)))
        .thenReturn(new GlobalRestResponse<>(true, Map.of("conversationId", CONVERSATION_ID)));

    Object conversation = resumeTailorService.start(RESUME_ID, JOB_ID, "es", CALLER);

    ArgumentCaptor<StartTailorConversationRequest> captor =
        ArgumentCaptor.forClass(StartTailorConversationRequest.class);
    verify(lynqAgentClient).startConversation(captor.capture(), eq(REQUEST_UUID), eq(USER_ID));
    StartTailorConversationRequest request = captor.getValue();
    assertThat(request.getJob().getId(), is(JOB_ID));
    assertThat(request.getJob().getTitle(), is(JOB_TITLE));
    assertThat(request.getJob().getDescription(), is(JOB_DESCRIPTION));
    assertThat(request.getJob().getCompany(), is(COMPANY_NAME));
    assertThat(request.getJob().getWorkType(), is("REMOTE"));
    assertThat(request.getJob().getSkills(), contains("Java", "Kubernetes"));
    assertThat(request.getBaseResumeId(), is(RESUME_ID));
    assertThat(request.getBaseResume(), is(BASE_RESUME));
    assertThat(conversation, is(Map.of("conversationId", CONVERSATION_ID)));
  }

  @Test
  void startTellsTheAgentToSpeakTheUiLanguageAndEditInTheResumesOwn() {
    givenTheCallersResumes();
    givenTheJob();
    when(lynqAgentClient.startConversation(any(), eq(REQUEST_UUID), eq(USER_ID)))
        .thenReturn(new GlobalRestResponse<>(true, Map.of()));

    resumeTailorService.start(RESUME_ID, JOB_ID, "es", CALLER);

    ArgumentCaptor<StartTailorConversationRequest> captor =
        ArgumentCaptor.forClass(StartTailorConversationRequest.class);
    verify(lynqAgentClient).startConversation(captor.capture(), eq(REQUEST_UUID), eq(USER_ID));
    assertThat(captor.getValue().getLanguage(), is("ES"));
    assertThat(captor.getValue().getResumeLanguage(), is(RESUME_LANGUAGE));
  }

  @Test
  void startRejectsAResumeThatDoesNotBelongToTheCaller() {
    givenTheCallersResumes();

    BadRequestException failure = assertThrows(BadRequestException.class,
        () -> resumeTailorService.start(OTHER_RESUME_ID, JOB_ID, "EN", CALLER));

    assertThat(failure.getMessage(), is("Resume '" + OTHER_RESUME_ID + "' not found"));
    verify(lynqAgentClient, never()).startConversation(any(), any(), any());
  }

  @Test
  void startAnswersNotFoundWhenTheJobPostingDoesNotExist() {
    givenTheCallersResumes();
    when(lynqBackendClient.getJobDetails(JOB_ID, REQUEST_UUID, AUTHORIZATION))
        .thenThrow(feignFailure(404, "{\"reason\":\"Job post not found\"}"));

    assertThrows(NotFoundException.class,
        () -> resumeTailorService.start(RESUME_ID, JOB_ID, "EN", CALLER));
    verify(lynqAgentClient, never()).startConversation(any(), any(), any());
  }

  @Test
  void startReportsABadGatewayWhenTheAgentFails() {
    givenTheCallersResumes();
    givenTheJob();
    when(lynqAgentClient.startConversation(any(), eq(REQUEST_UUID), eq(USER_ID)))
        .thenThrow(feignFailure(502, "{\"reason\":\"The LLM failed\"}"));

    assertThrows(BadGatewayException.class,
        () -> resumeTailorService.start(RESUME_ID, JOB_ID, "EN", CALLER));
  }

  @Test
  void turnRelaysTheMessageAndTheIdempotencyKey() {
    when(lynqAgentClient.takeTurn(eq(CONVERSATION_ID), any(), eq(REQUEST_UUID), eq(USER_ID)))
        .thenReturn(new GlobalRestResponse<>(true, Map.of("reply", "Done")));

    Object turn = resumeTailorService.turn(CONVERSATION_ID, " Highlight Kubernetes ",
        TURN_KEY, CALLER);

    ArgumentCaptor<TailorTurnRequest> captor = ArgumentCaptor.forClass(TailorTurnRequest.class);
    verify(lynqAgentClient).takeTurn(eq(CONVERSATION_ID), captor.capture(), eq(REQUEST_UUID),
        eq(USER_ID));
    assertThat(captor.getValue().getMessage(), is("Highlight Kubernetes"));
    assertThat(captor.getValue().getTurnKey(), is(TURN_KEY));
    assertThat(turn, is(Map.of("reply", "Done")));
  }

  @Test
  void turnRejectsAnEmptyMessage() {
    BadRequestException failure = assertThrows(BadRequestException.class,
        () -> resumeTailorService.turn(CONVERSATION_ID, "  ", TURN_KEY, CALLER));

    assertThat(failure.getMessage(), is("A message is required"));
    verify(lynqAgentClient, never()).takeTurn(any(), any(), any(), any());
  }

  @Test
  void turnRejectsAMissingIdempotencyKey() {
    BadRequestException failure = assertThrows(BadRequestException.class,
        () -> resumeTailorService.turn(CONVERSATION_ID, "Go ahead", null, CALLER));

    assertThat(failure.getMessage(), is("A turn key is required"));
    verify(lynqAgentClient, never()).takeTurn(any(), any(), any(), any());
  }

  @Test
  void turnKeepsTheAgentsConflictCode() {
    when(lynqAgentClient.takeTurn(eq(CONVERSATION_ID), any(), eq(REQUEST_UUID), eq(USER_ID)))
        .thenThrow(feignFailure(409, "{\"success\":false,\"reason\":\"A turn is already running "
            + "on this conversation\",\"code\":\"TURN_IN_PROGRESS\"}"));

    ConflictException failure = assertThrows(ConflictException.class,
        () -> resumeTailorService.turn(CONVERSATION_ID, "Go ahead", TURN_KEY, CALLER));

    assertThat(failure.getCode(), is("TURN_IN_PROGRESS"));
    assertThat(failure.getMessage(), is("A turn is already running on this conversation"));
  }

  @Test
  void turnKeepsTheExhaustedConflictCode() {
    when(lynqAgentClient.takeTurn(eq(CONVERSATION_ID), any(), eq(REQUEST_UUID), eq(USER_ID)))
        .thenThrow(feignFailure(409, "{\"reason\":\"No turns left\","
            + "\"code\":\"CONVERSATION_EXHAUSTED\"}"));

    ConflictException failure = assertThrows(ConflictException.class,
        () -> resumeTailorService.turn(CONVERSATION_ID, "Go ahead", TURN_KEY, CALLER));

    assertThat(failure.getCode(), is("CONVERSATION_EXHAUSTED"));
  }

  @Test
  void viewReturnsTheConversationAsTheAgentKeepsIt() {
    givenTheConversation();

    assertThat(resumeTailorService.view(CONVERSATION_ID, CALLER), is(CONVERSATION));
  }

  @Test
  void viewAnswersNotFoundForAConversationThatDoesNotExist() {
    when(lynqAgentClient.getConversation(CONVERSATION_ID, REQUEST_UUID, USER_ID))
        .thenThrow(feignFailure(404, "{\"reason\":\"Conversation does not exist\"}"));

    NotFoundException failure = assertThrows(NotFoundException.class,
        () -> resumeTailorService.view(CONVERSATION_ID, CALLER));

    assertThat(failure.getMessage(), is("Conversation does not exist"));
  }

  @Test
  void viewAnswersForbiddenForSomeoneElsesConversation() {
    when(lynqAgentClient.getConversation(CONVERSATION_ID, REQUEST_UUID, USER_ID))
        .thenThrow(feignFailure(403, "{\"reason\":\"Conversation belongs to another user\"}"));

    assertThrows(ForbiddenException.class,
        () -> resumeTailorService.view(CONVERSATION_ID, CALLER));
  }

  @Test
  void applyAppliesWithTheTailoredResumeAndClosesTheConversation() {
    givenTheCallersResumes();
    givenTheConversation();
    when(lynqBackendClient.applyToJob(eq(JOB_ID), any(), eq(REQUEST_UUID), eq(AUTHORIZATION)))
        .thenReturn(new GlobalRestResponse<>(true, APPLICATION));
    givenTheConversationCloses();

    ResumeTailorApplyRestResponse applied =
        resumeTailorService.apply(CONVERSATION_ID, RESUME_ID, CALLER);

    ArgumentCaptor<ApplyJobRequest> application = ArgumentCaptor.forClass(ApplyJobRequest.class);
    verify(lynqBackendClient).applyToJob(eq(JOB_ID), application.capture(), eq(REQUEST_UUID),
        eq(AUTHORIZATION));
    assertThat(application.getValue().getResumeId(), is(RESUME_ID));

    ArgumentCaptor<MarkConversationAppliedRequest> closed =
        ArgumentCaptor.forClass(MarkConversationAppliedRequest.class);
    verify(lynqAgentClient).markApplied(eq(CONVERSATION_ID), closed.capture(), eq(REQUEST_UUID),
        eq(USER_ID));
    assertThat(closed.getValue().getAppliedResumeId(), is(RESUME_ID));

    assertThat(applied.getApplication(), is(APPLICATION));
    assertThat(applied.isAlreadyApplied(), is(false));
    assertThat(applied.getConversationStatus(), is("APPLIED"));
  }

  @Test
  void applyTakesTheJobFromTheConversationAndNotFromTheCaller() {
    givenTheCallersResumes();
    givenTheConversation();
    when(lynqBackendClient.applyToJob(eq(JOB_ID), any(), eq(REQUEST_UUID), eq(AUTHORIZATION)))
        .thenReturn(new GlobalRestResponse<>(true, APPLICATION));
    givenTheConversationCloses();

    resumeTailorService.apply(CONVERSATION_ID, RESUME_ID, CALLER);

    verify(lynqAgentClient).getConversation(CONVERSATION_ID, REQUEST_UUID, USER_ID);
    verify(lynqBackendClient).applyToJob(eq(JOB_ID), any(), eq(REQUEST_UUID), eq(AUTHORIZATION));
  }

  @Test
  void applyClosesTheConversationWhenTheCandidateHadAlreadyAppliedToTheJob() {
    givenTheCallersResumes();
    givenTheConversation();
    when(lynqBackendClient.applyToJob(eq(JOB_ID), any(), eq(REQUEST_UUID), eq(AUTHORIZATION)))
        .thenThrow(feignFailure(400, "{\"reason\":\"User has already applied to this job\"}"));
    givenTheConversationCloses();

    ResumeTailorApplyRestResponse applied =
        resumeTailorService.apply(CONVERSATION_ID, RESUME_ID, CALLER);

    assertThat(applied.isAlreadyApplied(), is(true));
    assertThat(applied.getApplication(), is(nullValue()));
    assertThat(applied.getConversationStatus(), is("APPLIED"));
    verify(lynqAgentClient).markApplied(eq(CONVERSATION_ID), any(), eq(REQUEST_UUID), eq(USER_ID));
  }

  @Test
  void applyRejectsAResumeThatDoesNotBelongToTheCaller() {
    givenTheCallersResumes();

    assertThrows(BadRequestException.class,
        () -> resumeTailorService.apply(CONVERSATION_ID, OTHER_RESUME_ID, CALLER));

    verify(lynqBackendClient, never()).applyToJob(any(), any(), any(), any());
    verify(lynqAgentClient, never()).markApplied(any(), any(), any(), any());
  }

  @Test
  void applyRejectsAMissingResumeId() {
    BadRequestException failure = assertThrows(BadRequestException.class,
        () -> resumeTailorService.apply(CONVERSATION_ID, " ", CALLER));

    assertThat(failure.getMessage(), is("A resume id is required"));
    verify(lynqBackendClient, never()).applyToJob(any(), any(), any(), any());
  }

  @Test
  void applyKeepsTheApplicationWhenTheConversationCannotBeClosed() {
    givenTheCallersResumes();
    givenTheConversation();
    when(lynqBackendClient.applyToJob(eq(JOB_ID), any(), eq(REQUEST_UUID), eq(AUTHORIZATION)))
        .thenReturn(new GlobalRestResponse<>(true, APPLICATION));
    when(lynqAgentClient.markApplied(eq(CONVERSATION_ID), any(), eq(REQUEST_UUID), eq(USER_ID)))
        .thenThrow(feignFailure(502, "{\"reason\":\"The agent is down\"}"));

    ResumeTailorApplyRestResponse applied =
        resumeTailorService.apply(CONVERSATION_ID, RESUME_ID, CALLER);

    assertThat(applied.getApplication(), is(APPLICATION));
    assertThat(applied.getConversationStatus(), is(nullValue()));
  }

  @Test
  void applySurfacesTheAgentsAlreadyAppliedConflict() {
    givenTheCallersResumes();
    givenTheConversation();
    when(lynqBackendClient.applyToJob(eq(JOB_ID), any(), eq(REQUEST_UUID), eq(AUTHORIZATION)))
        .thenReturn(new GlobalRestResponse<>(true, APPLICATION));
    when(lynqAgentClient.markApplied(eq(CONVERSATION_ID), any(), eq(REQUEST_UUID), eq(USER_ID)))
        .thenThrow(feignFailure(409, "{\"reason\":\"Already applied with another resume\","
            + "\"code\":\"ALREADY_APPLIED\"}"));

    ConflictException failure = assertThrows(ConflictException.class,
        () -> resumeTailorService.apply(CONVERSATION_ID, RESUME_ID, CALLER));

    assertThat(failure.getCode(), is("ALREADY_APPLIED"));
  }

  @Test
  void applyReportsABadGatewayWhenTheApplicationCannotBeCreated() {
    givenTheCallersResumes();
    givenTheConversation();
    when(lynqBackendClient.applyToJob(eq(JOB_ID), any(), eq(REQUEST_UUID), eq(AUTHORIZATION)))
        .thenThrow(feignFailure(500, "{\"reason\":\"boom\"}"));

    assertThrows(BadGatewayException.class,
        () -> resumeTailorService.apply(CONVERSATION_ID, RESUME_ID, CALLER));
    verify(lynqAgentClient, never()).markApplied(any(), any(), any(), any());
  }

  @Test
  void applyReportsABadGatewayWhenTheConversationCarriesNoJob() {
    givenTheCallersResumes();
    when(lynqAgentClient.getConversation(CONVERSATION_ID, REQUEST_UUID, USER_ID))
        .thenReturn(new GlobalRestResponse<>(true, Map.of("status", "ACTIVE")));

    assertThrows(BadGatewayException.class,
        () -> resumeTailorService.apply(CONVERSATION_ID, RESUME_ID, CALLER));
    verify(lynqBackendClient, never()).applyToJob(any(), any(), any(), any());
  }

  @Test
  void readingTheResumesIsReportedAsABadGateway() {
    when(lynqBackendClient.getUserResumes(REQUEST_UUID, AUTHORIZATION))
        .thenThrow(new IllegalStateException("connection refused"));

    BadGatewayException failure = assertThrows(BadGatewayException.class,
        () -> resumeTailorService.start(RESUME_ID, JOB_ID, "EN", CALLER));

    assertThat(failure.getMessage(), is("The caller's resumes could not be read"));
  }

  private void givenTheCallersResumes() {
    when(lynqBackendClient.getUserResumes(REQUEST_UUID, AUTHORIZATION))
        .thenReturn(new GlobalRestResponse<>(true, List.of(UserResumeResponse.builder()
            .id(RESUME_ID)
            .language(RESUME_LANGUAGE)
            .resume(BASE_RESUME)
            .build())));
  }

  private void givenTheJob() {
    when(lynqBackendClient.getJobDetails(JOB_ID, REQUEST_UUID, AUTHORIZATION))
        .thenReturn(new GlobalRestResponse<>(true, JobDetailsResponse.builder()
            .jobId(JOB_ID)
            .title(JOB_TITLE)
            .description(JOB_DESCRIPTION)
            .workType("REMOTE")
            .skills(JOB_SKILLS)
            .company(JobDetailsResponse.JobCompanyResponse.builder()
                .id("company-1")
                .name(COMPANY_NAME)
                .build())
            .build()));
  }

  private void givenTheConversation() {
    when(lynqAgentClient.getConversation(CONVERSATION_ID, REQUEST_UUID, USER_ID))
        .thenReturn(new GlobalRestResponse<>(true, CONVERSATION));
  }

  private void givenTheConversationCloses() {
    lenient().when(lynqAgentClient.markApplied(eq(CONVERSATION_ID), any(), eq(REQUEST_UUID),
            eq(USER_ID)))
        .thenReturn(new GlobalRestResponse<>(true, Map.of("status", "APPLIED")));
  }

  private FeignException feignFailure(int status, String body) {
    Request request = Request.create(Request.HttpMethod.POST, "/dmz/conversation",
        Map.of(), new byte[0], StandardCharsets.UTF_8, new RequestTemplate());

    return FeignException.errorStatus("call", Response.builder()
        .status(status)
        .reason("failed")
        .request(request)
        .headers(Map.of())
        .body(body, StandardCharsets.UTF_8)
        .build());
  }
}
