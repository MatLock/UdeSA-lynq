package com.lynq.backend.service;

import com.lynq.backend.client.LynqMLClient;
import com.lynq.backend.client.response.CandidateExplanationResponse;
import com.lynq.backend.client.response.Course;
import com.lynq.backend.client.response.QuerySuggestion;
import com.lynq.backend.client.response.UpskillingSuggestionResponse;
import com.lynq.backend.controller.request.CandidateEvaluationRequest;
import com.lynq.backend.controller.request.CandidateSpecRequest;
import com.lynq.backend.controller.request.JobSpecRequest;
import com.lynq.backend.controller.response.GlobalRestResponse;
import com.lynq.backend.enums.UserType;
import com.lynq.backend.exceptions.BadRequestException;
import com.lynq.backend.model.CompanyEntity;
import com.lynq.backend.model.UserEntity;
import com.lynq.backend.repository.CompanyRepository;
import com.lynq.backend.repository.UserRepository;
import com.lynq.backend.security.LynqUserPrincipal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LynqMLProxyServiceCandidateEvaluationTest {

  private static final String USER_ID = "550e8400-e29b-41d4-a716-446655440000";
  private static final String USERNAME = "janedoe";
  private static final String EMAIL = "jane@lynq.com";
  private static final String COMPANY_ID = "company-1";
  private static final String REQUEST_UUID = "11111111-1111-1111-1111-111111111111";

  private static final String JOB_DESCRIPTION = "Build and scale the Lynq hiring platform.";
  private static final String CANDIDATE_DESCRIPTION = "Backend engineer with 5 years of experience.";
  private static final String SKILL_JAVA = "Java";
  private static final String SKILL_SPRING = "Spring";
  private static final String SKILL_KUBERNETES = "Kubernetes";
  private static final List<String> JOB_SKILLS = List.of(SKILL_JAVA, SKILL_SPRING, SKILL_KUBERNETES);
  private static final List<String> CANDIDATE_SKILLS = List.of(SKILL_JAVA, SKILL_SPRING);

  private static final String OUTCOME = "The candidate should strengthen container orchestration.";
  private static final String QUERY = "Kubernetes orchestration";
  private static final String COURSE_TITLE = "Kubernetes for Developers";
  private static final String COURSE_URL = "https://udemy.com/kubernetes";

  private static final String RECOMMENDATION = "maybe";
  private static final String EXPLANATION = "Strong core stack but missing infrastructure depth.";
  private static final String STRENGTH = "Solid Java and Spring experience";
  private static final String CONCERN = "No Kubernetes exposure";

  private static final String ONLY_COMPANY_USERS_CAN_EVALUATE_CANDIDATES =
      "Only users of type COMPANY can evaluate candidates";
  private static final String USER_NOT_LINKED_TO_COMPANY = "User is not linked to any company";
  private static final String AUTHENTICATED_USER_NOT_FOUND = "Authenticated user not found";

  @Mock
  private LynqMLClient lynqMLClient;

  @Mock
  private UserRepository userRepository;

  @Mock
  private CompanyRepository companyRepository;

  @Mock
  private SecurityContext securityContext;

  @Mock
  private Authentication authentication;

  private LynqMLProxyService lynqMLProxyService;

  @BeforeEach
  void setUp() {
    lynqMLProxyService = new LynqMLProxyService(lynqMLClient, userRepository, companyRepository);
    SecurityContextHolder.setContext(securityContext);
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void suggestUpskillingForwardsMappedRequestAndHeadersToClient() {
    stubAuthenticatedCompanyUserWithCompany(companyUser());
    when(lynqMLClient.upskillingSuggestion(any(), eq(REQUEST_UUID), eq(USER_ID), eq(COMPANY_ID)))
        .thenReturn(new GlobalRestResponse<>(true, upskillingResponse()));
    ArgumentCaptor<com.lynq.backend.client.request.CandidateEvaluationRequest> requestCaptor =
        ArgumentCaptor.forClass(com.lynq.backend.client.request.CandidateEvaluationRequest.class);

    lynqMLProxyService.suggestUpskilling(evaluationRequest(), REQUEST_UUID);

    verify(lynqMLClient).upskillingSuggestion(requestCaptor.capture(), eq(REQUEST_UUID),
        eq(USER_ID), eq(COMPANY_ID));
    com.lynq.backend.client.request.CandidateEvaluationRequest forwarded = requestCaptor.getValue();
    assertThat(forwarded.getJob().getDescription(), is(JOB_DESCRIPTION));
    assertThat(forwarded.getJob().getSkills(), is(JOB_SKILLS));
    assertThat(forwarded.getCandidate().getDescription(), is(CANDIDATE_DESCRIPTION));
    assertThat(forwarded.getCandidate().getSkills(), is(CANDIDATE_SKILLS));
  }

  @Test
  void suggestUpskillingReturnsDataFromClientResponse() {
    stubAuthenticatedCompanyUserWithCompany(companyUser());
    UpskillingSuggestionResponse mlResponse = upskillingResponse();
    when(lynqMLClient.upskillingSuggestion(any(), eq(REQUEST_UUID), eq(USER_ID), eq(COMPANY_ID)))
        .thenReturn(new GlobalRestResponse<>(true, mlResponse));

    UpskillingSuggestionResponse result =
        lynqMLProxyService.suggestUpskilling(evaluationRequest(), REQUEST_UUID);

    assertThat(result, is(sameInstance(mlResponse)));
    assertThat(result.getOutcome(), is(OUTCOME));
    assertThat(result.getSuggestions().get(0).getQuery(), is(QUERY));
    assertThat(result.getSuggestions().get(0).getCourses().get(0).getTitle(), is(COURSE_TITLE));
  }

  @Test
  void suggestUpskillingThrowsBadRequestWhenAuthenticatedUserNotFound() {
    stubAuthenticatedPrincipal();
    when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

    BadRequestException exception = assertThrows(BadRequestException.class,
        () -> lynqMLProxyService.suggestUpskilling(evaluationRequest(), REQUEST_UUID));
    assertThat(exception.getMessage(), is(AUTHENTICATED_USER_NOT_FOUND));
    verify(lynqMLClient, never()).upskillingSuggestion(any(), any(), any(), any());
  }

  @Test
  void suggestUpskillingThrowsBadRequestWhenUserIsNotCompanyType() {
    stubAuthenticatedPrincipal();
    when(userRepository.findById(USER_ID))
        .thenReturn(Optional.of(UserEntity.builder().id(USER_ID).type(UserType.CANDIDATE).build()));

    BadRequestException exception = assertThrows(BadRequestException.class,
        () -> lynqMLProxyService.suggestUpskilling(evaluationRequest(), REQUEST_UUID));
    assertThat(exception.getMessage(), is(ONLY_COMPANY_USERS_CAN_EVALUATE_CANDIDATES));
    verify(lynqMLClient, never()).upskillingSuggestion(any(), any(), any(), any());
  }

  @Test
  void suggestUpskillingThrowsBadRequestWhenUserNotLinkedToCompany() {
    UserEntity user = companyUser();
    stubAuthenticatedPrincipal();
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
    when(companyRepository.findByOwner(user)).thenReturn(Optional.empty());

    BadRequestException exception = assertThrows(BadRequestException.class,
        () -> lynqMLProxyService.suggestUpskilling(evaluationRequest(), REQUEST_UUID));
    assertThat(exception.getMessage(), is(USER_NOT_LINKED_TO_COMPANY));
    verify(lynqMLClient, never()).upskillingSuggestion(any(), any(), any(), any());
  }

  @Test
  void explainCandidateForwardsMappedRequestAndHeadersToClient() {
    stubAuthenticatedCompanyUserWithCompany(companyUser());
    when(lynqMLClient.candidateExplanation(any(), eq(REQUEST_UUID), eq(USER_ID), eq(COMPANY_ID)))
        .thenReturn(new GlobalRestResponse<>(true, explanationResponse()));
    ArgumentCaptor<com.lynq.backend.client.request.CandidateEvaluationRequest> requestCaptor =
        ArgumentCaptor.forClass(com.lynq.backend.client.request.CandidateEvaluationRequest.class);

    lynqMLProxyService.explainCandidate(evaluationRequest(), REQUEST_UUID);

    verify(lynqMLClient).candidateExplanation(requestCaptor.capture(), eq(REQUEST_UUID),
        eq(USER_ID), eq(COMPANY_ID));
    com.lynq.backend.client.request.CandidateEvaluationRequest forwarded = requestCaptor.getValue();
    assertThat(forwarded.getJob().getSkills(), is(JOB_SKILLS));
    assertThat(forwarded.getCandidate().getSkills(), is(CANDIDATE_SKILLS));
  }

  @Test
  void explainCandidateReturnsDataFromClientResponse() {
    stubAuthenticatedCompanyUserWithCompany(companyUser());
    CandidateExplanationResponse mlResponse = explanationResponse();
    when(lynqMLClient.candidateExplanation(any(), eq(REQUEST_UUID), eq(USER_ID), eq(COMPANY_ID)))
        .thenReturn(new GlobalRestResponse<>(true, mlResponse));

    CandidateExplanationResponse result =
        lynqMLProxyService.explainCandidate(evaluationRequest(), REQUEST_UUID);

    assertThat(result, is(sameInstance(mlResponse)));
    assertThat(result.getRecommendation(), is(RECOMMENDATION));
    assertThat(result.getExplanation(), is(EXPLANATION));
    assertThat(result.getStrengths(), contains(STRENGTH));
    assertThat(result.getConcerns(), contains(CONCERN));
  }

  @Test
  void explainCandidateThrowsBadRequestWhenUserIsNotCompanyType() {
    stubAuthenticatedPrincipal();
    when(userRepository.findById(USER_ID))
        .thenReturn(Optional.of(UserEntity.builder().id(USER_ID).type(UserType.CANDIDATE).build()));

    BadRequestException exception = assertThrows(BadRequestException.class,
        () -> lynqMLProxyService.explainCandidate(evaluationRequest(), REQUEST_UUID));
    assertThat(exception.getMessage(), is(ONLY_COMPANY_USERS_CAN_EVALUATE_CANDIDATES));
    verify(lynqMLClient, never()).candidateExplanation(any(), any(), any(), any());
  }

  @Test
  void explainCandidateThrowsBadRequestWhenUserNotLinkedToCompany() {
    UserEntity user = companyUser();
    stubAuthenticatedPrincipal();
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
    when(companyRepository.findByOwner(user)).thenReturn(Optional.empty());

    BadRequestException exception = assertThrows(BadRequestException.class,
        () -> lynqMLProxyService.explainCandidate(evaluationRequest(), REQUEST_UUID));
    assertThat(exception.getMessage(), is(USER_NOT_LINKED_TO_COMPANY));
    verify(lynqMLClient, never()).candidateExplanation(any(), any(), any(), any());
  }

  private CandidateEvaluationRequest evaluationRequest() {
    JobSpecRequest job = new JobSpecRequest();
    job.setDescription(JOB_DESCRIPTION);
    job.setSkills(JOB_SKILLS);
    CandidateSpecRequest candidate = new CandidateSpecRequest();
    candidate.setDescription(CANDIDATE_DESCRIPTION);
    candidate.setSkills(CANDIDATE_SKILLS);
    CandidateEvaluationRequest request = new CandidateEvaluationRequest();
    request.setJob(job);
    request.setCandidate(candidate);
    return request;
  }

  private UpskillingSuggestionResponse upskillingResponse() {
    Course course = Course.builder().title(COURSE_TITLE).url(COURSE_URL).build();
    QuerySuggestion suggestion =
        QuerySuggestion.builder().query(QUERY).courses(List.of(course)).build();
    return UpskillingSuggestionResponse.builder()
        .outcome(OUTCOME)
        .suggestions(List.of(suggestion))
        .build();
  }

  private CandidateExplanationResponse explanationResponse() {
    return CandidateExplanationResponse.builder()
        .recommendation(RECOMMENDATION)
        .explanation(EXPLANATION)
        .strengths(List.of(STRENGTH))
        .concerns(List.of(CONCERN))
        .build();
  }

  private UserEntity companyUser() {
    return UserEntity.builder().id(USER_ID).type(UserType.COMPANY).build();
  }

  private void stubAuthenticatedPrincipal() {
    when(securityContext.getAuthentication()).thenReturn(authentication);
    when(authentication.getPrincipal())
        .thenReturn(new LynqUserPrincipal(USER_ID, USERNAME, EMAIL));
  }

  private void stubAuthenticatedCompanyUserWithCompany(UserEntity user) {
    CompanyEntity company = CompanyEntity.builder().id(COMPANY_ID).owner(user).build();
    stubAuthenticatedPrincipal();
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
    when(companyRepository.findByOwner(user)).thenReturn(Optional.of(company));
  }
}
