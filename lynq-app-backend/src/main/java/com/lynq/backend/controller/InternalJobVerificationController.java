package com.lynq.backend.controller;

import com.lynq.backend.controller.request.LivenessReportsRequest;
import com.lynq.backend.controller.response.ExpireJobPostsRestResponse;
import com.lynq.backend.controller.response.GlobalRestResponse;
import com.lynq.backend.controller.response.LivenessRestResponse;
import com.lynq.backend.controller.response.VerificationCandidatesRestResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;

@Tag(name = "Internal", description = "Service-to-service operations, not reachable from the DMZ")
public interface InternalJobVerificationController {

  @Operation(
      summary = "List the external job posts due for a liveness check",
      description = "Returns the open external job posts with a job URL that were last seen on "
          + "a portal more than lynq.verification.window-days ago (20 by default) and were not "
          + "checked today. At most lynq.verification.quota-per-category posts per category "
          + "(10 by default), the never-checked first and then the longest since their last "
          + "check. The list interleaves the categories: every category's first candidate, then "
          + "every category's second, so a caller that only checks the first few still spreads "
          + "them across categories. Authenticated with the shared internal token header.")
  ResponseEntity<GlobalRestResponse<VerificationCandidatesRestResponse>> candidates();

  @Operation(
      summary = "Report what a liveness check found",
      description = "Applies a batch of {id, outcome}. ALIVE renews last_seen_on and "
          + "last_checked_on; CLOSED and GONE close the job post today with close_reason "
          + "VERIFIED_CLOSED or VERIFIED_GONE and publish JobPostClosed; UNKNOWN only stamps "
          + "last_checked_on, which sends the post to the back of the next run's queue. A report "
          + "for an unknown id, a LYNQ job post or a post that is no longer open is skipped. "
          + "Answers the count of each outcome applied and of the skipped reports. Authenticated "
          + "with the shared internal token header.")
  ResponseEntity<GlobalRestResponse<LivenessRestResponse>> liveness(
      @Valid LivenessReportsRequest request);

  @Operation(
      summary = "Close the external job posts nobody has seen for too long",
      description = "Closes today, with close_reason EXPIRED_BY_POLICY, every open external job "
          + "post last seen on a portal more than lynq.verification.expire-after-days ago (25 by "
          + "default), with or without a job URL, and publishes JobPostClosed for each. Analytics "
          + "treats these closes as censored: the post was still open when the policy closed it. "
          + "Answers how many were closed. Authenticated with the shared internal token header.")
  ResponseEntity<GlobalRestResponse<ExpireJobPostsRestResponse>> expire();
}
