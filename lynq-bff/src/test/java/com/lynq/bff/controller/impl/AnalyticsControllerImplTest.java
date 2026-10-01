package com.lynq.bff.controller.impl;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lynq.bff.client.response.JobSalaryResponse;
import com.lynq.bff.client.response.JobStandingResponse;
import com.lynq.bff.client.response.JobTimeToFillResponse;
import com.lynq.bff.controller.response.GlobalRestResponse;
import com.lynq.bff.security.LynqUserPrincipal;
import com.lynq.bff.service.AnalyticsService;
import com.lynq.bff.service.Caller;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

@ExtendWith(MockitoExtension.class)
class AnalyticsControllerImplTest {

  private static final String USER_ID = "11111111-1111-1111-1111-111111111111";
  private static final String REQUEST_UUID = "018f9c3a-2b1d-7c4e-9a6f-1e2d3c4b5a99";
  private static final String AUTHORIZATION = "Bearer access-token";
  private static final String JOB_ID = "018f9c3a-2b1d-7c4e-9a6f-1e2d3c4b5a70";

  private static final LynqUserPrincipal PRINCIPAL = new LynqUserPrincipal(
      USER_ID, "janedoe", "jane@lynq.com",
      List.of(new SimpleGrantedAuthority("R_CANDIDATE")), AUTHORIZATION);

  @Mock
  private AnalyticsService analyticsService;

  private AnalyticsControllerImpl analyticsController;

  @BeforeEach
  void setUp() {
    analyticsController = new AnalyticsControllerImpl(analyticsService);
  }

  @Test
  void answersTheTimeToFillWithOk() {
    JobTimeToFillResponse timeToFill = JobTimeToFillResponse.builder().n(12).build();
    when(analyticsService.getTimeToFill(eq(JOB_ID), any())).thenReturn(timeToFill);

    ResponseEntity<GlobalRestResponse<JobTimeToFillResponse>> response =
        analyticsController.getTimeToFill(JOB_ID, REQUEST_UUID, PRINCIPAL);

    assertThat(response.getStatusCode(), is(HttpStatus.OK));
    assertThat(response.getBody().isSuccess(), is(true));
    assertThat(response.getBody().getData(), is(sameInstance(timeToFill)));
  }

  @Test
  void answersTheStandingWithOk() {
    JobStandingResponse standing = JobStandingResponse.builder().rank(3).build();
    when(analyticsService.getStanding(eq(JOB_ID), any())).thenReturn(standing);

    ResponseEntity<GlobalRestResponse<JobStandingResponse>> response =
        analyticsController.getStanding(JOB_ID, REQUEST_UUID, PRINCIPAL);

    assertThat(response.getStatusCode(), is(HttpStatus.OK));
    assertThat(response.getBody().getData(), is(sameInstance(standing)));
  }

  @Test
  void answersTheSalaryInsightsWithOk() {
    JobSalaryResponse salary = JobSalaryResponse.builder().build();
    when(analyticsService.getSalary(eq(JOB_ID), any())).thenReturn(salary);

    ResponseEntity<GlobalRestResponse<JobSalaryResponse>> response =
        analyticsController.getSalary(JOB_ID, REQUEST_UUID, PRINCIPAL);

    assertThat(response.getStatusCode(), is(HttpStatus.OK));
    assertThat(response.getBody().getData(), is(sameInstance(salary)));
  }

  @Test
  void callsTheServiceAsTheVerifiedCaller() {
    analyticsController.getStanding(JOB_ID, REQUEST_UUID, PRINCIPAL);

    ArgumentCaptor<Caller> caller = ArgumentCaptor.forClass(Caller.class);
    verify(analyticsService).getStanding(eq(JOB_ID), caller.capture());
    assertThat(caller.getValue().userId(), is(USER_ID));
    assertThat(caller.getValue().requestUuid(), is(REQUEST_UUID));
    assertThat(caller.getValue().authorization(), is(AUTHORIZATION));
  }
}
