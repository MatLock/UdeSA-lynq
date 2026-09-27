package com.lynq.bff.controller;

import com.lynq.bff.client.request.ApplyJobRequest;
import com.lynq.bff.client.request.CreateJobRequest;
import com.lynq.bff.client.request.UpdateJobRequest;
import com.lynq.bff.client.response.ApplyJobResponse;
import com.lynq.bff.client.response.CandidateExplanationResponse;
import com.lynq.bff.client.response.CloseJobResponse;
import com.lynq.bff.client.response.CreateJobResponse;
import com.lynq.bff.client.response.GetJobResponse;
import com.lynq.bff.client.response.JobCandidateResponse;
import com.lynq.bff.client.response.JobDetailsResponse;
import com.lynq.bff.client.response.PagedResponse;
import com.lynq.bff.client.response.RefreshJobResponse;
import com.lynq.bff.client.response.UpdateJobResponse;
import com.lynq.bff.client.response.UpskillingSuggestionResponse;
import com.lynq.bff.controller.response.GlobalRestResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.http.ResponseEntity;

@ApiResponses({
    @ApiResponse(responseCode = "401", description = "The Authorization header is missing, or the "
        + "access token's signature is invalid or expired."),
    @ApiResponse(responseCode = "403", description = "The lynq-request-uuid header is missing, or "
        + "the caller does not hold the role the route requires."),
    @ApiResponse(responseCode = "502", description = "lynq-app-backend could not be reached.")
})
public interface JobController {

  @Operation(summary = "Publish a job post")
  ResponseEntity<GlobalRestResponse<CreateJobResponse>> createJob(
      CreateJobRequest request,
      @Parameter(hidden = true) String requestUuid,
      @Parameter(hidden = true) String authorization,
      @Parameter(hidden = true) String userId);

  @Operation(summary = "Edit a job post")
  ResponseEntity<GlobalRestResponse<UpdateJobResponse>> updateJob(
      String jobId,
      UpdateJobRequest request,
      @Parameter(hidden = true) String requestUuid,
      @Parameter(hidden = true) String authorization,
      @Parameter(hidden = true) String userId);

  @Operation(summary = "Search the open job posts")
  ResponseEntity<GlobalRestResponse<PagedResponse<GetJobResponse>>> getJobs(
      Integer page,
      Integer size,
      String filterValue,
      @Parameter(hidden = true) String requestUuid,
      @Parameter(hidden = true) String authorization,
      @Parameter(hidden = true) String userId);

  @Operation(summary = "List the job posts of the authenticated user's company")
  ResponseEntity<GlobalRestResponse<PagedResponse<GetJobResponse>>> getMyJobs(
      Integer page,
      Integer size,
      @Parameter(hidden = true) String requestUuid,
      @Parameter(hidden = true) String authorization,
      @Parameter(hidden = true) String userId);

  @Operation(summary = "Read a job post as a candidate sees it")
  @ApiResponses(@ApiResponse(responseCode = "404", description = "No job post holds that id."))
  ResponseEntity<GlobalRestResponse<JobDetailsResponse>> getJobDetails(
      String jobId,
      @Parameter(hidden = true) String requestUuid,
      @Parameter(hidden = true) String authorization,
      @Parameter(hidden = true) String userId);

  @Operation(summary = "Count one more view of a job post")
  ResponseEntity<GlobalRestResponse<Long>> increaseSeen(
      String jobId,
      @Parameter(hidden = true) String requestUuid,
      @Parameter(hidden = true) String authorization,
      @Parameter(hidden = true) String userId);

  @Operation(summary = "Move a job post back to the top of the feed")
  ResponseEntity<GlobalRestResponse<RefreshJobResponse>> refreshJob(
      String jobId,
      @Parameter(hidden = true) String requestUuid,
      @Parameter(hidden = true) String authorization,
      @Parameter(hidden = true) String userId);

  @Operation(summary = "Close a job post")
  ResponseEntity<GlobalRestResponse<CloseJobResponse>> closeJob(
      String jobId,
      @Parameter(hidden = true) String requestUuid,
      @Parameter(hidden = true) String authorization,
      @Parameter(hidden = true) String userId);

  @Operation(summary = "Apply to a job post")
  ResponseEntity<GlobalRestResponse<ApplyJobResponse>> applyToJob(
      String jobId,
      ApplyJobRequest request,
      @Parameter(hidden = true) String requestUuid,
      @Parameter(hidden = true) String authorization,
      @Parameter(hidden = true) String userId);

  @Operation(summary = "List the candidates that applied to a job post")
  ResponseEntity<GlobalRestResponse<PagedResponse<JobCandidateResponse>>> getCandidates(
      String jobId,
      Integer page,
      Integer pageSize,
      @Parameter(hidden = true) String requestUuid,
      @Parameter(hidden = true) String authorization,
      @Parameter(hidden = true) String userId);

  @Operation(summary = "Explain why a candidate fits a job post")
  ResponseEntity<GlobalRestResponse<CandidateExplanationResponse>> explainCandidate(
      String jobId,
      String candidateId,
      @Parameter(hidden = true) String requestUuid,
      @Parameter(hidden = true) String authorization,
      @Parameter(hidden = true) String userId);

  @Operation(summary = "Suggest what to learn to fit a job post")
  ResponseEntity<GlobalRestResponse<UpskillingSuggestionResponse>> suggestUpskilling(
      String jobId,
      String language,
      @Parameter(hidden = true) String requestUuid,
      @Parameter(hidden = true) String authorization,
      @Parameter(hidden = true) String userId);
}
