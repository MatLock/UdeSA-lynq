package com.lynq.analytics.controller.impl;

import com.lynq.analytics.aspect.AuditLog;
import com.lynq.analytics.controller.AnalyticsController;
import com.lynq.analytics.controller.response.GlobalRestResponse;
import com.lynq.analytics.security.HasRole;
import com.lynq.analytics.security.Role;
import com.lynq.analytics.service.CandidateBenchmarkQueryService;
import com.lynq.analytics.service.CompanyJobsService;
import com.lynq.analytics.service.MarketService;
import com.lynq.analytics.service.SalaryService;
import com.lynq.analytics.service.StandingService;
import com.lynq.analytics.stats.CandidateBenchmark;
import com.lynq.analytics.stats.CompanyJobs;
import com.lynq.analytics.stats.Market;
import com.lynq.analytics.stats.SalaryInsights;
import com.lynq.analytics.stats.Standing;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/dmz/analytics")
public class AnalyticsControllerImpl implements AnalyticsController {

  private final StandingService standingService;
  private final SalaryService salaryService;
  private final CandidateBenchmarkQueryService candidateBenchmarkQueryService;
  private final MarketService marketService;
  private final CompanyJobsService companyJobsService;

  public AnalyticsControllerImpl(StandingService standingService, SalaryService salaryService,
      CandidateBenchmarkQueryService candidateBenchmarkQueryService, MarketService marketService,
      CompanyJobsService companyJobsService) {
    this.standingService = standingService;
    this.salaryService = salaryService;
    this.candidateBenchmarkQueryService = candidateBenchmarkQueryService;
    this.marketService = marketService;
    this.companyJobsService = companyJobsService;
  }

  @Override
  @GetMapping("/job/{jobId}/standing")
  @HasRole(Role.CANDIDATE)
  @AuditLog
  public ResponseEntity<GlobalRestResponse<Standing>> getStanding(
      @PathVariable String jobId,
      @AuthenticationPrincipal(expression = "id") String userId) {
    Standing standing = standingService.standing(jobId, userId);

    return ResponseEntity
        .status(HttpStatus.OK)
        .body(new GlobalRestResponse<>(true, standing));
  }

  @Override
  @GetMapping("/job/{jobId}/salary")
  @AuditLog
  public ResponseEntity<GlobalRestResponse<SalaryInsights>> getSalary(@PathVariable String jobId) {
    SalaryInsights salary = salaryService.salary(jobId);

    return ResponseEntity
        .status(HttpStatus.OK)
        .body(new GlobalRestResponse<>(true, salary));
  }

  @Override
  @GetMapping("/candidate/me/benchmark")
  @HasRole(Role.CANDIDATE)
  @AuditLog
  public ResponseEntity<GlobalRestResponse<CandidateBenchmark>> getCandidateBenchmark(
      @AuthenticationPrincipal(expression = "id") String userId) {
    CandidateBenchmark benchmark = candidateBenchmarkQueryService.benchmark(userId);

    return ResponseEntity
        .status(HttpStatus.OK)
        .body(new GlobalRestResponse<>(true, benchmark));
  }

  @Override
  @GetMapping("/market")
  @AuditLog
  public ResponseEntity<GlobalRestResponse<Market>> getMarket(
      @RequestParam(defaultValue = "ARS") String currency) {
    Market market = marketService.market(currency);

    return ResponseEntity
        .status(HttpStatus.OK)
        .body(new GlobalRestResponse<>(true, market));
  }

  @Override
  @GetMapping("/company/me/jobs")
  @HasRole(Role.COMPANY)
  @AuditLog
  public ResponseEntity<GlobalRestResponse<CompanyJobs>> getCompanyJobs(
      @AuthenticationPrincipal(expression = "id") String userId) {
    CompanyJobs jobs = companyJobsService.jobs(userId);

    return ResponseEntity
        .status(HttpStatus.OK)
        .body(new GlobalRestResponse<>(true, jobs));
  }
}
