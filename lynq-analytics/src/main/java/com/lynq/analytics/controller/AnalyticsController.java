package com.lynq.analytics.controller;

import com.lynq.analytics.controller.response.GlobalRestResponse;
import com.lynq.analytics.stats.CandidateBenchmark;
import com.lynq.analytics.stats.CompanyJobs;
import com.lynq.analytics.stats.Market;
import com.lynq.analytics.stats.SalaryInsights;
import com.lynq.analytics.stats.Standing;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "Analytics", description = "The numbers that sit next to a job post")
public interface AnalyticsController {

  @Operation(
      summary = "Read the caller's standing among the applicants of a job post",
      description = "Returns the caller's rank, the number of applicants, the caller's percentile "
          + "rank among them, the score the backend computed when the caller applied and the "
          + "median score of the job post. The rank is one plus the applicants who scored higher, "
          + "so ties share a rank; the percentile counts the applicants below and half of those "
          + "tied, the caller included. The median is null below five applicants, where it would "
          + "give away the scores of the others. No other applicant is identified. Only CANDIDATE "
          + "users who applied to the job post may read it: fails with 403 for any other caller "
          + "and with 404 when the job post is unknown. Cached for an hour per job post and "
          + "candidate.",
      security = @SecurityRequirement(name = "bearerAuth"))
  ResponseEntity<GlobalRestResponse<Standing>> getStanding(
      String jobId,
      @Parameter(hidden = true) String userId);

  @Operation(
      summary = "Read the salary medians of a job post's position and of similar candidates",
      description = "Returns two blocks in the currency of the job post, ARS when it has no "
          + "salary. positionSalary summarises the job posts similar to this one that publish a "
          + "salary in that currency, each one counted at the middle of its range; "
          + "peersExpectedSalary summarises the expected salary that candidates similar to the job "
          + "post declared in that currency. Each block carries the median, p25, p75, the number "
          + "of values and insufficientData: below five values the median and quartiles are null, "
          + "since they would give away the few salaries behind them. Open to any authenticated "
          + "user; fails with 404 when the job post is unknown. Cached for an hour per job post.",
      security = @SecurityRequirement(name = "bearerAuth"))
  ResponseEntity<GlobalRestResponse<SalaryInsights>> getSalary(String jobId);

  @Operation(
      summary = "Read the caller's market fit and their position among similar candidates",
      description = "Returns the latest row the 05:00 job wrote for the caller: the market fit "
          + "(median score against the open job posts relevant to their tags), the number of job "
          + "posts scored, the share of them above the reach threshold, the threshold itself, the "
          + "caller's percentile among candidates with similar tags and the size of that group, "
          + "the peers' quartiles, the caller's skill coverage against the median of the peers, "
          + "the skills that would take most job posts above the threshold, and the daily series "
          + "of the last 90 days. The fit and the reach are null below five relevant job posts, "
          + "and the percentile and the peers' figures below five peers. Before the job has "
          + "written a row for the caller, snapshotOn is null and the lists are empty. Always the "
          + "authenticated candidate: there is no user id in the path. Only CANDIDATE users; "
          + "cached per candidate until the next snapshot.",
      security = @SecurityRequirement(name = "bearerAuth"))
  ResponseEntity<GlobalRestResponse<CandidateBenchmark>> getCandidateBenchmark(
      @Parameter(hidden = true) String userId);

  @Operation(
      summary = "Read the market the platform sees",
      description = "Returns the latest daily snapshot of the open job posts: how many there "
          + "are and how many publish a salary, the most demanded skills with the change against "
          + "the snapshot a week earlier, and the salary of the open job posts by category and "
          + "work type in the requested currency, withheld below five posts. Also returns the "
          + "job posts published in each of the last twelve complete weeks, counted from the "
          + "read model. currency is ARS or USD, ARS by default; any other fails with 400. Open "
          + "to any authenticated user; cached per currency until the next snapshot or for an "
          + "hour.",
      security = @SecurityRequirement(name = "bearerAuth"))
  ResponseEntity<GlobalRestResponse<Market>> getMarket(String currency);

  @Operation(
      summary = "Compare the job posts of the authenticated company",
      description = "Returns every job post the caller published, newest first, with its "
          + "status, the number of applications and the median score of its applicants, null "
          + "below five applicants. Always the authenticated company: there is no id in the "
          + "path. Only COMPANY users; cached per user for an hour.",
      security = @SecurityRequirement(name = "bearerAuth"))
  ResponseEntity<GlobalRestResponse<CompanyJobs>> getCompanyJobs(
      @Parameter(hidden = true) String userId);
}
