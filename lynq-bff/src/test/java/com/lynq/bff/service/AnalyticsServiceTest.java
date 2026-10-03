package com.lynq.bff.service;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.lynq.bff.client.LynqAnalyticsClient;
import com.lynq.bff.client.response.CandidateBenchmarkResponse;
import com.lynq.bff.client.response.CompanyJobsResponse;
import com.lynq.bff.client.response.JobSalaryResponse;
import com.lynq.bff.client.response.JobStandingResponse;
import com.lynq.bff.client.response.JobTimeToFillResponse;
import com.lynq.bff.client.response.MarketResponse;
import com.lynq.bff.controller.response.GlobalRestResponse;
import com.lynq.bff.exceptions.BadGatewayException;
import com.lynq.bff.exceptions.ForbiddenException;
import com.lynq.bff.exceptions.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AnalyticsServiceTest {

  private static final String USER_ID = "11111111-1111-1111-1111-111111111111";
  private static final String REQUEST_UUID = "018f9c3a-2b1d-7c4e-9a6f-1e2d3c4b5a99";
  private static final String AUTHORIZATION = "Bearer access-token";
  private static final Caller CALLER = new Caller(USER_ID, REQUEST_UUID, AUTHORIZATION);

  private static final String JOB_ID = "018f9c3a-2b1d-7c4e-9a6f-1e2d3c4b5a70";

  @Mock
  private LynqAnalyticsClient lynqAnalyticsClient;

  private AnalyticsService analyticsService;

  @BeforeEach
  void setUp() {
    analyticsService = new AnalyticsService(lynqAnalyticsClient);
  }

  @Test
  void readsTheTimeToFillOfTheJobPost() {
    JobTimeToFillResponse timeToFill = JobTimeToFillResponse.builder().n(12).median(21.0).build();
    when(lynqAnalyticsClient.getTimeToFill(JOB_ID, REQUEST_UUID, AUTHORIZATION))
        .thenReturn(new GlobalRestResponse<>(true, timeToFill));

    assertThat(analyticsService.getTimeToFill(JOB_ID, CALLER), is(sameInstance(timeToFill)));
  }

  @Test
  void readsTheStandingOfTheCaller() {
    JobStandingResponse standing = JobStandingResponse.builder().rank(3).totalApplicants(40).build();
    when(lynqAnalyticsClient.getStanding(JOB_ID, REQUEST_UUID, AUTHORIZATION))
        .thenReturn(new GlobalRestResponse<>(true, standing));

    assertThat(analyticsService.getStanding(JOB_ID, CALLER), is(sameInstance(standing)));
  }

  @Test
  void readsTheSalaryInsightsOfTheJobPost() {
    JobSalaryResponse salary = JobSalaryResponse.builder().build();
    when(lynqAnalyticsClient.getSalary(JOB_ID, REQUEST_UUID, AUTHORIZATION))
        .thenReturn(new GlobalRestResponse<>(true, salary));

    assertThat(analyticsService.getSalary(JOB_ID, CALLER), is(sameInstance(salary)));
  }

  @Test
  void keepsTheRefusalOfLynqAnalyticsWhenTheCallerLacksTheRole() {
    when(lynqAnalyticsClient.getStanding(JOB_ID, REQUEST_UUID, AUTHORIZATION))
        .thenThrow(FeignErrors.status(403, """
            {"success": false, "reason": "Only users of type CANDIDATE can perform this action"}"""));

    ForbiddenException thrown = assertThrows(ForbiddenException.class,
        () -> analyticsService.getStanding(JOB_ID, CALLER));

    assertThat(thrown.getMessage(), is("Only users of type CANDIDATE can perform this action"));
  }

  @Test
  void keepsTheNotFoundOfLynqAnalyticsForAnUnknownJobPost() {
    when(lynqAnalyticsClient.getTimeToFill(JOB_ID, REQUEST_UUID, AUTHORIZATION))
        .thenThrow(FeignErrors.status(404));

    NotFoundException thrown = assertThrows(NotFoundException.class,
        () -> analyticsService.getTimeToFill(JOB_ID, CALLER));

    assertThat(thrown.getMessage(), is("The time to fill could not be read"));
  }

  @Test
  void answersBadGatewayWhenLynqAnalyticsCannotBeReached() {
    when(lynqAnalyticsClient.getSalary(JOB_ID, REQUEST_UUID, AUTHORIZATION))
        .thenThrow(FeignErrors.unreachable());

    BadGatewayException thrown = assertThrows(BadGatewayException.class,
        () -> analyticsService.getSalary(JOB_ID, CALLER));

    assertThat(thrown.getMessage(), is("The salary insights could not be read"));
  }

  @Test
  void readsTheBenchmarkOfTheCaller() {
    CandidateBenchmarkResponse benchmark =
        CandidateBenchmarkResponse.builder().marketFit(61).peerGroupSize(38).build();
    when(lynqAnalyticsClient.getCandidateBenchmark(REQUEST_UUID, AUTHORIZATION))
        .thenReturn(new GlobalRestResponse<>(true, benchmark));

    assertThat(analyticsService.getCandidateBenchmark(CALLER), is(sameInstance(benchmark)));
  }

  @Test
  void readsTheMarketInTheRequestedCurrency() {
    MarketResponse market = MarketResponse.builder().openJobPosts(30).build();
    when(lynqAnalyticsClient.getMarket("USD", REQUEST_UUID, AUTHORIZATION))
        .thenReturn(new GlobalRestResponse<>(true, market));

    assertThat(analyticsService.getMarket("USD", CALLER), is(sameInstance(market)));
  }

  @Test
  void readsTheJobPostsOfTheCallingCompany() {
    CompanyJobsResponse jobs = CompanyJobsResponse.builder().build();
    when(lynqAnalyticsClient.getCompanyJobs(REQUEST_UUID, AUTHORIZATION))
        .thenReturn(new GlobalRestResponse<>(true, jobs));

    assertThat(analyticsService.getCompanyJobs(CALLER), is(sameInstance(jobs)));
  }

  @Test
  void keepsTheRefusalOfTheCompanyJobPostsToACandidate() {
    when(lynqAnalyticsClient.getCompanyJobs(REQUEST_UUID, AUTHORIZATION))
        .thenThrow(FeignErrors.status(403, """
            {"success": false, "reason": "Only users of type COMPANY can perform this action"}"""));

    ForbiddenException thrown = assertThrows(ForbiddenException.class,
        () -> analyticsService.getCompanyJobs(CALLER));

    assertThat(thrown.getMessage(), is("Only users of type COMPANY can perform this action"));
  }

  @Test
  void answersBadGatewayWhenTheMarketCannotBeReached() {
    when(lynqAnalyticsClient.getMarket(null, REQUEST_UUID, AUTHORIZATION))
        .thenThrow(FeignErrors.unreachable());

    BadGatewayException thrown = assertThrows(BadGatewayException.class,
        () -> analyticsService.getMarket(null, CALLER));

    assertThat(thrown.getMessage(), is("The market could not be read"));
  }
}
