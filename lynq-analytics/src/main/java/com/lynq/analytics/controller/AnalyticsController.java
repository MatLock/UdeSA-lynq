package com.lynq.analytics.controller;

import com.lynq.analytics.controller.response.GlobalRestResponse;
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
}
