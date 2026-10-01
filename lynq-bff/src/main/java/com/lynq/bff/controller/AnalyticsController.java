package com.lynq.bff.controller;

import com.lynq.bff.client.response.JobSalaryResponse;
import com.lynq.bff.client.response.JobStandingResponse;
import com.lynq.bff.client.response.JobTimeToFillResponse;
import com.lynq.bff.controller.response.GlobalRestResponse;
import com.lynq.bff.security.LynqUserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.http.ResponseEntity;

@ApiResponses({
    @ApiResponse(responseCode = "401", description = "The Authorization header is missing, or the "
        + "access token's signature is invalid or expired."),
    @ApiResponse(responseCode = "403", description = "The lynq-request-uuid header is missing, or "
        + "lynq-analytics refused the caller for the route."),
    @ApiResponse(responseCode = "404", description = "lynq-analytics holds no job post with that "
        + "id."),
    @ApiResponse(responseCode = "502", description = "lynq-analytics could not be reached.")
})
public interface AnalyticsController {

  @Operation(
      summary = "Read the days similar job posts took to close",
      description = "Median, p25, p75 and N of similar closed job posts. Posts closed by the "
          + "25-day policy are censored: they stay out of the median and are counted apart in "
          + "`expiredByPolicy`. Only the company that owns the job post may read it.")
  ResponseEntity<GlobalRestResponse<JobTimeToFillResponse>> getTimeToFill(
      String jobId,
      @Parameter(hidden = true) String requestUuid,
      @Parameter(hidden = true) LynqUserPrincipal principal);

  @Operation(
      summary = "Read the caller's standing among the applicants of a job post",
      description = "Rank, total applicants, percentile, score and median score. Ties share a "
          + "rank and no other applicant is identified. Only a candidate who applied may read it.")
  ResponseEntity<GlobalRestResponse<JobStandingResponse>> getStanding(
      String jobId,
      @Parameter(hidden = true) String requestUuid,
      @Parameter(hidden = true) LynqUserPrincipal principal);

  @Operation(
      summary = "Read the salaries of similar job posts and of similar candidates",
      description = "`positionSalary` covers similar job posts with a salary in the same currency; "
          + "`peersExpectedSalary` covers candidates whose tags match the job post's and who "
          + "declared an expected salary.")
  ResponseEntity<GlobalRestResponse<JobSalaryResponse>> getSalary(
      String jobId,
      @Parameter(hidden = true) String requestUuid,
      @Parameter(hidden = true) LynqUserPrincipal principal);
}
