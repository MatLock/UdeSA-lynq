package com.lynq.backend.controller.impl;

import com.lynq.backend.controller.request.LivenessReportRequest;
import com.lynq.backend.controller.request.LivenessReportsRequest;
import com.lynq.backend.controller.response.ExpireJobPostsRestResponse;
import com.lynq.backend.controller.response.GlobalRestResponse;
import com.lynq.backend.controller.response.LivenessRestResponse;
import com.lynq.backend.controller.response.VerificationCandidatesRestResponse;
import com.lynq.backend.service.JobVerificationService;
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
class InternalJobVerificationControllerImplTest {

  @Mock
  private JobVerificationService jobVerificationService;

  @Mock
  private LivenessReportsRequest request;

  @Mock
  private LivenessReportRequest report;

  private InternalJobVerificationControllerImpl controller;

  @BeforeEach
  void setUp() {
    controller = new InternalJobVerificationControllerImpl(jobVerificationService);
  }

  @Test
  void candidatesAnswerOkWithTheServiceListInsideTheEnvelope() {
    VerificationCandidatesRestResponse candidates = new VerificationCandidatesRestResponse(List.of());
    when(jobVerificationService.candidates()).thenReturn(candidates);

    ResponseEntity<GlobalRestResponse<VerificationCandidatesRestResponse>> response =
        controller.candidates();

    assertThat(response.getStatusCode(), is(HttpStatus.OK));
    assertThat(response.getBody().isSuccess(), is(true));
    assertThat(response.getBody().getData(), is(sameInstance(candidates)));
  }

  @Test
  void livenessHandsTheReportsToTheServiceAndAnswersItsCounts() {
    LivenessRestResponse counts = LivenessRestResponse.builder().alive(3).gone(1).build();
    when(request.getReports()).thenReturn(List.of(report));
    when(jobVerificationService.report(List.of(report))).thenReturn(counts);

    ResponseEntity<GlobalRestResponse<LivenessRestResponse>> response = controller.liveness(request);

    assertThat(response.getStatusCode(), is(HttpStatus.OK));
    assertThat(response.getBody().getData(), is(sameInstance(counts)));
  }

  @Test
  void expireAnswersHowManyPostsWereClosed() {
    ExpireJobPostsRestResponse expired = new ExpireJobPostsRestResponse(7);
    when(jobVerificationService.expire()).thenReturn(expired);

    ResponseEntity<GlobalRestResponse<ExpireJobPostsRestResponse>> response = controller.expire();

    assertThat(response.getStatusCode(), is(HttpStatus.OK));
    assertThat(response.getBody().getData().getExpired(), is(7));
  }
}
