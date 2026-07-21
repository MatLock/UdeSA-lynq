package com.lynq.backend.controller.impl;

import com.lynq.backend.client.response.CandidateExplanationResponse;
import com.lynq.backend.client.response.Course;
import com.lynq.backend.client.response.QuerySuggestion;
import com.lynq.backend.client.response.UpskillingSuggestionResponse;
import com.lynq.backend.controller.request.CandidateEvaluationRequest;
import com.lynq.backend.controller.response.GlobalRestResponse;
import com.lynq.backend.service.LynqMLProxyService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LynqMLProxyControllerImplCandidateEvaluationTest {

  private static final String REQUEST_UUID = "550e8400-e29b-41d4-a716-446655440000";

  private static final String OUTCOME = "The candidate should strengthen container orchestration.";
  private static final String QUERY = "Kubernetes orchestration";
  private static final String COURSE_TITLE = "Kubernetes for Developers";
  private static final String COURSE_URL = "https://udemy.com/kubernetes";

  private static final String RECOMMENDATION = "maybe";
  private static final String EXPLANATION = "Strong core stack but missing infrastructure depth.";
  private static final String STRENGTH = "Solid Java and Spring experience";
  private static final String CONCERN = "No Kubernetes exposure";

  @Mock
  private LynqMLProxyService lynqMLProxyService;

  @Mock
  private CandidateEvaluationRequest request;

  private LynqMLProxyControllerImpl lynqMLProxyController;

  @BeforeEach
  void setUp() {
    lynqMLProxyController = new LynqMLProxyControllerImpl(lynqMLProxyService);
    lenient().when(lynqMLProxyService.suggestUpskilling(request, REQUEST_UUID))
        .thenReturn(upskillingResponse());
    lenient().when(lynqMLProxyService.explainCandidate(request, REQUEST_UUID))
        .thenReturn(explanationResponse());
  }

  @Test
  void suggestUpskillingDelegatesToServiceWithRequestAndRequestUuid() {
    lynqMLProxyController.suggestUpskilling(request, REQUEST_UUID);

    verify(lynqMLProxyService).suggestUpskilling(request, REQUEST_UUID);
  }

  @Test
  void suggestUpskillingRespondsWithOkStatusAndWrapsBody() {
    ResponseEntity<GlobalRestResponse<UpskillingSuggestionResponse>> response =
        lynqMLProxyController.suggestUpskilling(request, REQUEST_UUID);

    assertThat(response.getStatusCode(), is(HttpStatus.OK));
    assertThat(response.getBody(), is(notNullValue()));
    assertThat(response.getBody().isSuccess(), is(true));
  }

  @Test
  void suggestUpskillingMapsServiceResponseIntoData() {
    ResponseEntity<GlobalRestResponse<UpskillingSuggestionResponse>> response =
        lynqMLProxyController.suggestUpskilling(request, REQUEST_UUID);

    UpskillingSuggestionResponse data = response.getBody().getData();
    assertThat(data.getOutcome(), is(OUTCOME));
    assertThat(data.getSuggestions().get(0).getQuery(), is(QUERY));
  }

  @Test
  void explainCandidateDelegatesToServiceWithRequestAndRequestUuid() {
    lynqMLProxyController.explainCandidate(request, REQUEST_UUID);

    verify(lynqMLProxyService).explainCandidate(request, REQUEST_UUID);
  }

  @Test
  void explainCandidateRespondsWithOkStatusAndWrapsBody() {
    ResponseEntity<GlobalRestResponse<CandidateExplanationResponse>> response =
        lynqMLProxyController.explainCandidate(request, REQUEST_UUID);

    assertThat(response.getStatusCode(), is(HttpStatus.OK));
    assertThat(response.getBody(), is(notNullValue()));
    assertThat(response.getBody().isSuccess(), is(true));
  }

  @Test
  void explainCandidateMapsServiceResponseIntoData() {
    ResponseEntity<GlobalRestResponse<CandidateExplanationResponse>> response =
        lynqMLProxyController.explainCandidate(request, REQUEST_UUID);

    CandidateExplanationResponse data = response.getBody().getData();
    assertThat(data.getRecommendation(), is(RECOMMENDATION));
    assertThat(data.getExplanation(), is(EXPLANATION));
    assertThat(data.getStrengths(), contains(STRENGTH));
    assertThat(data.getConcerns(), contains(CONCERN));
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
}
