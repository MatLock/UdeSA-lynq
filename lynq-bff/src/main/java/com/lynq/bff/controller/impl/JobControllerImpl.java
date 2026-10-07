package com.lynq.bff.controller.impl;

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
import com.lynq.bff.controller.JobController;
import com.lynq.bff.controller.response.GlobalRestResponse;
import com.lynq.bff.ratelimit.RateLimitTier;
import com.lynq.bff.ratelimit.RateLimited;
import com.lynq.bff.security.LynqUserPrincipal;
import com.lynq.bff.service.Caller;
import com.lynq.bff.service.JobService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/job")
public class JobControllerImpl implements JobController {

  private static final String REQUEST_UUID_HEADER = "lynq-request-uuid";

  private final JobService jobService;

  public JobControllerImpl(JobService jobService) {
    this.jobService = jobService;
  }

  @Override
  @PostMapping
  public ResponseEntity<GlobalRestResponse<CreateJobResponse>> createJob(
      @RequestBody CreateJobRequest request,
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid,
      @AuthenticationPrincipal LynqUserPrincipal principal) {
    CreateJobResponse created =
        jobService.createJob(request, caller(principal, requestUuid));

    return ResponseEntity
        .status(HttpStatus.CREATED)
        .body(new GlobalRestResponse<>(true, created));
  }

  @Override
  @PatchMapping("/{jobId}")
  public ResponseEntity<GlobalRestResponse<UpdateJobResponse>> updateJob(
      @PathVariable String jobId,
      @RequestBody UpdateJobRequest request,
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid,
      @AuthenticationPrincipal LynqUserPrincipal principal) {
    UpdateJobResponse updated =
        jobService.updateJob(jobId, request, caller(principal, requestUuid));

    return ResponseEntity
        .status(HttpStatus.OK)
        .body(new GlobalRestResponse<>(true, updated));
  }

  @Override
  @GetMapping
  public ResponseEntity<GlobalRestResponse<PagedResponse<GetJobResponse>>> getJobs(
      @RequestParam(defaultValue = "0") Integer page,
      @RequestParam(defaultValue = "10") Integer size,
      @RequestParam(required = false) String filterValue,
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid,
      @AuthenticationPrincipal LynqUserPrincipal principal) {
    PagedResponse<GetJobResponse> jobs = jobService.getJobs(
        page, size, filterValue, caller(principal, requestUuid));

    return ResponseEntity
        .status(HttpStatus.OK)
        .body(new GlobalRestResponse<>(true, jobs));
  }

  @Override
  @GetMapping("/mine")
  public ResponseEntity<GlobalRestResponse<PagedResponse<GetJobResponse>>> getMyJobs(
      @RequestParam(defaultValue = "0") Integer page,
      @RequestParam(defaultValue = "10") Integer size,
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid,
      @AuthenticationPrincipal LynqUserPrincipal principal) {
    PagedResponse<GetJobResponse> jobs =
        jobService.getMyJobs(page, size, caller(principal, requestUuid));

    return ResponseEntity
        .status(HttpStatus.OK)
        .body(new GlobalRestResponse<>(true, jobs));
  }

  @Override
  @GetMapping("/{jobId}/details")
  public ResponseEntity<GlobalRestResponse<JobDetailsResponse>> getJobDetails(
      @PathVariable String jobId,
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid,
      @AuthenticationPrincipal LynqUserPrincipal principal) {
    JobDetailsResponse job =
        jobService.getJobDetails(jobId, caller(principal, requestUuid));

    return ResponseEntity
        .status(HttpStatus.OK)
        .body(new GlobalRestResponse<>(true, job));
  }

  @Override
  @PatchMapping("/{jobId}/increase-seen")
  public ResponseEntity<GlobalRestResponse<Long>> increaseSeen(
      @PathVariable String jobId,
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid,
      @AuthenticationPrincipal LynqUserPrincipal principal) {
    Long totalSeen =
        jobService.increaseSeen(jobId, caller(principal, requestUuid));

    return ResponseEntity
        .status(HttpStatus.OK)
        .body(new GlobalRestResponse<>(true, totalSeen));
  }

  @Override
  @PatchMapping("/{jobId}/refresh")
  public ResponseEntity<GlobalRestResponse<RefreshJobResponse>> refreshJob(
      @PathVariable String jobId,
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid,
      @AuthenticationPrincipal LynqUserPrincipal principal) {
    RefreshJobResponse refreshed =
        jobService.refreshJob(jobId, caller(principal, requestUuid));

    return ResponseEntity
        .status(HttpStatus.OK)
        .body(new GlobalRestResponse<>(true, refreshed));
  }

  @Override
  @PatchMapping("/{jobId}/close")
  public ResponseEntity<GlobalRestResponse<CloseJobResponse>> closeJob(
      @PathVariable String jobId,
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid,
      @AuthenticationPrincipal LynqUserPrincipal principal) {
    CloseJobResponse closed =
        jobService.closeJob(jobId, caller(principal, requestUuid));

    return ResponseEntity
        .status(HttpStatus.OK)
        .body(new GlobalRestResponse<>(true, closed));
  }

  @Override
  @PostMapping("/{jobId}/apply")
  public ResponseEntity<GlobalRestResponse<ApplyJobResponse>> applyToJob(
      @PathVariable String jobId,
      @RequestBody ApplyJobRequest request,
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid,
      @AuthenticationPrincipal LynqUserPrincipal principal) {
    ApplyJobResponse applied =
        jobService.applyToJob(jobId, request, caller(principal, requestUuid));

    return ResponseEntity
        .status(HttpStatus.CREATED)
        .body(new GlobalRestResponse<>(true, applied));
  }

  @Override
  @GetMapping("/{jobId}/candidates")
  public ResponseEntity<GlobalRestResponse<PagedResponse<JobCandidateResponse>>> getCandidates(
      @PathVariable String jobId,
      @RequestParam(defaultValue = "0") Integer page,
      @RequestParam(defaultValue = "10") Integer pageSize,
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid,
      @AuthenticationPrincipal LynqUserPrincipal principal) {
    PagedResponse<JobCandidateResponse> candidates = jobService.getCandidates(
        jobId, page, pageSize, caller(principal, requestUuid));

    return ResponseEntity
        .status(HttpStatus.OK)
        .body(new GlobalRestResponse<>(true, candidates));
  }

  @Override
  @GetMapping("/{jobId}/candidate/{candidateId}/candidate-explanation")
  @RateLimited(RateLimitTier.STANDARD)
  public ResponseEntity<GlobalRestResponse<CandidateExplanationResponse>> explainCandidate(
      @PathVariable String jobId,
      @PathVariable String candidateId,
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid,
      @AuthenticationPrincipal LynqUserPrincipal principal) {
    CandidateExplanationResponse explanation = jobService.explainCandidate(
        jobId, candidateId, caller(principal, requestUuid));

    return ResponseEntity
        .status(HttpStatus.OK)
        .body(new GlobalRestResponse<>(true, explanation));
  }

  @Override
  @GetMapping("/{jobId}/upskilling-suggestion")
  @RateLimited(RateLimitTier.STANDARD)
  public ResponseEntity<GlobalRestResponse<UpskillingSuggestionResponse>> suggestUpskilling(
      @PathVariable String jobId,
      @RequestParam(defaultValue = "en") String language,
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid,
      @AuthenticationPrincipal LynqUserPrincipal principal) {
    UpskillingSuggestionResponse suggestion = jobService.suggestUpskilling(
        jobId, language, caller(principal, requestUuid));

    return ResponseEntity
        .status(HttpStatus.OK)
        .body(new GlobalRestResponse<>(true, suggestion));
  }

  private static Caller caller(LynqUserPrincipal principal, String requestUuid) {
    return new Caller(principal.getId(), requestUuid, principal.getAuthorization());
  }
}
