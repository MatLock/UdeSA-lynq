package com.lynq.bff.controller.impl;

import com.lynq.bff.client.response.CandidateBenchmarkResponse;
import com.lynq.bff.client.response.CompanyJobsResponse;
import com.lynq.bff.client.response.JobSalaryResponse;
import com.lynq.bff.client.response.JobStandingResponse;
import com.lynq.bff.client.response.JobTimeToFillResponse;
import com.lynq.bff.client.response.MarketResponse;
import com.lynq.bff.controller.AnalyticsController;
import com.lynq.bff.controller.response.GlobalRestResponse;
import com.lynq.bff.security.LynqUserPrincipal;
import com.lynq.bff.service.AnalyticsService;
import com.lynq.bff.service.Caller;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/analytics")
public class AnalyticsControllerImpl implements AnalyticsController {

  private static final String REQUEST_UUID_HEADER = "lynq-request-uuid";

  private final AnalyticsService analyticsService;

  public AnalyticsControllerImpl(AnalyticsService analyticsService) {
    this.analyticsService = analyticsService;
  }

  @Override
  @GetMapping("/job/{jobId}/time-to-fill")
  public ResponseEntity<GlobalRestResponse<JobTimeToFillResponse>> getTimeToFill(
      @PathVariable String jobId,
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid,
      @AuthenticationPrincipal LynqUserPrincipal principal) {
    JobTimeToFillResponse timeToFill =
        analyticsService.getTimeToFill(jobId, caller(principal, requestUuid));

    return ResponseEntity
        .status(HttpStatus.OK)
        .body(new GlobalRestResponse<>(true, timeToFill));
  }

  @Override
  @GetMapping("/job/{jobId}/standing")
  public ResponseEntity<GlobalRestResponse<JobStandingResponse>> getStanding(
      @PathVariable String jobId,
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid,
      @AuthenticationPrincipal LynqUserPrincipal principal) {
    JobStandingResponse standing =
        analyticsService.getStanding(jobId, caller(principal, requestUuid));

    return ResponseEntity
        .status(HttpStatus.OK)
        .body(new GlobalRestResponse<>(true, standing));
  }

  @Override
  @GetMapping("/job/{jobId}/salary")
  public ResponseEntity<GlobalRestResponse<JobSalaryResponse>> getSalary(
      @PathVariable String jobId,
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid,
      @AuthenticationPrincipal LynqUserPrincipal principal) {
    JobSalaryResponse salary =
        analyticsService.getSalary(jobId, caller(principal, requestUuid));

    return ResponseEntity
        .status(HttpStatus.OK)
        .body(new GlobalRestResponse<>(true, salary));
  }

  @Override
  @GetMapping("/candidate/me/benchmark")
  public ResponseEntity<GlobalRestResponse<CandidateBenchmarkResponse>> getCandidateBenchmark(
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid,
      @AuthenticationPrincipal LynqUserPrincipal principal) {
    CandidateBenchmarkResponse benchmark =
        analyticsService.getCandidateBenchmark(caller(principal, requestUuid));

    return ResponseEntity
        .status(HttpStatus.OK)
        .body(new GlobalRestResponse<>(true, benchmark));
  }

  @Override
  @GetMapping("/market")
  public ResponseEntity<GlobalRestResponse<MarketResponse>> getMarket(
      @RequestParam(required = false) String currency,
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid,
      @AuthenticationPrincipal LynqUserPrincipal principal) {
    MarketResponse market = analyticsService.getMarket(currency, caller(principal, requestUuid));

    return ResponseEntity
        .status(HttpStatus.OK)
        .body(new GlobalRestResponse<>(true, market));
  }

  @Override
  @GetMapping("/company/me/jobs")
  public ResponseEntity<GlobalRestResponse<CompanyJobsResponse>> getCompanyJobs(
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid,
      @AuthenticationPrincipal LynqUserPrincipal principal) {
    CompanyJobsResponse jobs = analyticsService.getCompanyJobs(caller(principal, requestUuid));

    return ResponseEntity
        .status(HttpStatus.OK)
        .body(new GlobalRestResponse<>(true, jobs));
  }

  private static Caller caller(LynqUserPrincipal principal, String requestUuid) {
    return new Caller(principal.getId(), requestUuid, principal.getAuthorization());
  }
}
