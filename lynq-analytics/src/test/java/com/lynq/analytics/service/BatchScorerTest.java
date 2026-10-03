package com.lynq.analytics.service;

import com.lynq.analytics.client.LynqBackendClient;
import com.lynq.analytics.client.request.ScoreBatchRequest;
import com.lynq.analytics.client.request.ScoreBatchRequest.ScorePair;
import com.lynq.analytics.client.request.ScoreBatchRequest.ScoredProfile;
import com.lynq.analytics.client.response.ScoreBatchResponse;
import com.lynq.analytics.client.response.ScoreBatchResponse.PairScore;
import com.lynq.analytics.config.BenchmarkProperties;
import com.lynq.analytics.controller.response.GlobalRestResponse;
import com.lynq.analytics.service.BatchScorer.ProfilePair;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BatchScorerTest {

  private static final String INTERNAL_TOKEN = "the-internal-token";
  private static final String REQUEST_UUID = "550e8400-e29b-41d4-a716-446655440000";
  private static final int PAIRS_PER_REQUEST = 2;

  private static final ScoredProfile JOB_A = new ScoredProfile("job-a", List.of("Java"),
      List.of("Backend"));
  private static final ScoredProfile JOB_B = new ScoredProfile("job-b", List.of("Go"), List.of());
  private static final ScoredProfile CANDIDATE = new ScoredProfile("candidate", List.of("Java"),
      List.of("Backend"));

  @Mock
  private LynqBackendClient lynqBackendClient;

  private BatchScorer batchScorer;

  @BeforeEach
  void setUp() {
    batchScorer = new BatchScorer(lynqBackendClient, INTERNAL_TOKEN,
        new BenchmarkProperties(60, 5, 5, 1, 2, 5, PAIRS_PER_REQUEST, 90));
  }

  @AfterEach
  void tearDown() {
    MDC.clear();
  }

  @Test
  void describesEachProfileOnceAndListsThePairs() {
    ArgumentCaptor<ScoreBatchRequest> request = ArgumentCaptor.forClass(ScoreBatchRequest.class);
    when(lynqBackendClient.scoreBatch(eq(INTERNAL_TOKEN), anyString(), request.capture()))
        .thenReturn(answer(new PairScore("job-a", "candidate", 100),
            new PairScore("job-b", "candidate", 0)));

    Map<ScorePair, Integer> scores = batchScorer.score(List.of(
        new ProfilePair(JOB_A, CANDIDATE), new ProfilePair(JOB_B, CANDIDATE)));

    assertThat(request.getValue().jobPosts(), contains(JOB_A, JOB_B));
    assertThat(request.getValue().candidates(), contains(CANDIDATE));
    assertThat(request.getValue().pairs(), contains(new ScorePair("job-a", "candidate"),
        new ScorePair("job-b", "candidate")));
    assertThat(scores.get(new ScorePair("job-a", "candidate")), is(100));
    assertThat(scores.get(new ScorePair("job-b", "candidate")), is(0));
  }

  @Test
  void splitsThePairsIntoRequestsOfTheConfiguredSize() {
    ScoredProfile other = new ScoredProfile("other", List.of("Go"), List.of());
    when(lynqBackendClient.scoreBatch(eq(INTERNAL_TOKEN), anyString(), any()))
        .thenReturn(answer(new PairScore("job-a", "candidate", 100),
            new PairScore("job-b", "candidate", 0)))
        .thenReturn(answer(new PairScore("job-b", "other", 100)));

    Map<ScorePair, Integer> scores = batchScorer.score(List.of(
        new ProfilePair(JOB_A, CANDIDATE), new ProfilePair(JOB_B, CANDIDATE),
        new ProfilePair(JOB_B, other)));

    verify(lynqBackendClient, times(2)).scoreBatch(eq(INTERNAL_TOKEN), anyString(), any());
    assertThat(scores.size(), is(3));
  }

  @Test
  void sendsTheRequestIdOfTheRunningSnapshot() {
    MDC.put("requestId", REQUEST_UUID);
    when(lynqBackendClient.scoreBatch(INTERNAL_TOKEN, REQUEST_UUID,
        new ScoreBatchRequest(List.of(JOB_A), List.of(CANDIDATE),
            List.of(new ScorePair("job-a", "candidate")))))
        .thenReturn(answer(new PairScore("job-a", "candidate", 100)));

    assertThat(batchScorer.score(List.of(new ProfilePair(JOB_A, CANDIDATE))).values(),
        contains(100));
  }

  @Test
  void callsNothingWithoutPairs() {
    assertThat(batchScorer.score(List.of()).entrySet(), hasSize(0));

    verify(lynqBackendClient, never()).scoreBatch(anyString(), anyString(), any());
  }

  @Test
  void failsWhenTheBackendAnswersWithoutScores() {
    when(lynqBackendClient.scoreBatch(eq(INTERNAL_TOKEN), anyString(), any()))
        .thenReturn(new GlobalRestResponse<>(true, null));
    List<ProfilePair> pairs = List.of(new ProfilePair(JOB_A, CANDIDATE));

    assertThrows(IllegalStateException.class, () -> batchScorer.score(pairs));
  }

  private static GlobalRestResponse<ScoreBatchResponse> answer(PairScore... scores) {
    return new GlobalRestResponse<>(true, new ScoreBatchResponse(List.of(scores)));
  }
}
