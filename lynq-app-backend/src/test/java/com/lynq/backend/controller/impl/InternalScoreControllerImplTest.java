package com.lynq.backend.controller.impl;

import com.lynq.backend.controller.request.ScoreBatchRequest;
import com.lynq.backend.controller.response.GlobalRestResponse;
import com.lynq.backend.controller.response.PairScoreRestResponse;
import com.lynq.backend.controller.response.ScoreBatchRestResponse;
import com.lynq.backend.service.ScoreBatchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InternalScoreControllerImplTest {

  @Mock
  private ScoreBatchService scoreBatchService;

  @Mock
  private ScoreBatchRequest request;

  private InternalScoreControllerImpl controller;

  @BeforeEach
  void setUp() {
    controller = new InternalScoreControllerImpl(scoreBatchService);
  }

  @Test
  void answersTheScoresOfTheBatch() {
    ScoreBatchRestResponse scores = ScoreBatchRestResponse.builder()
        .scores(List.of(PairScoreRestResponse.builder()
            .jobId("job").candidateId("candidate").score(72).build()))
        .build();
    when(scoreBatchService.score(request)).thenReturn(scores);

    ResponseEntity<GlobalRestResponse<ScoreBatchRestResponse>> response =
        controller.scoreBatch(request);

    assertThat(response.getStatusCode(), is(HttpStatus.OK));
    assertThat(response.getBody().isSuccess(), is(true));
    assertThat(response.getBody().getData(), is(sameInstance(scores)));
  }
}
