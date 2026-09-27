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
import com.lynq.bff.filter.JwtSignatureFilter;
import com.lynq.bff.service.Caller;
import com.lynq.bff.service.JobService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/job")
public class JobControllerImpl implements JobController {

  private static final String REQUEST_UUID_HEADER = "lynq-request-uuid";
  private static final String AUTHORIZATION_HEADER = "Authorization";

  private final JobService jobService;

  public JobControllerImpl(JobService jobService) {
    this.jobService = jobService;
  }

  @Override
  @PostMapping
  public ResponseEntity<GlobalRestResponse<CreateJobResponse>> createJob(
      @RequestBody CreateJobRequest request,
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid,
      @RequestHeader(AUTHORIZATION_HEADER) String authorization,
      @RequestAttribute(JwtSignatureFilter.VERIFIED_USER_ID) String userId) {
    CreateJobResponse created =
        jobService.createJob(request, new Caller(userId, requestUuid, authorization));

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
      @RequestHeader(AUTHORIZATION_HEADER) String authorization,
      @RequestAttribute(JwtSignatureFilter.VERIFIED_USER_ID) String userId) {
    UpdateJobResponse updated =
        jobService.updateJob(jobId, request, new Caller(userId, requestUuid, authorization));

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
      @RequestHeader(AUTHORIZATION_HEADER) String authorization,
      @RequestAttribute(JwtSignatureFilter.VERIFIED_USER_ID) String userId) {
    PagedResponse<GetJobResponse> jobs = jobService.getJobs(
        page, size, filterValue, new Caller(userId, requestUuid, authorization));

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
      @RequestHeader(AUTHORIZATION_HEADER) String authorization,
      @RequestAttribute(JwtSignatureFilter.VERIFIED_USER_ID) String userId) {
    PagedResponse<GetJobResponse> jobs =
        jobService.getMyJobs(page, size, new Caller(userId, requestUuid, authorization));

    return ResponseEntity
        .status(HttpStatus.OK)
        .body(new GlobalRestResponse<>(true, jobs));
  }

  @Override
  @GetMapping("/{jobId}/details")
  public ResponseEntity<GlobalRestResponse<JobDetailsResponse>> getJobDetails(
      @PathVariable String jobId,
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid,
      @RequestHeader(AUTHORIZATION_HEADER) String authorization,
      @RequestAttribute(JwtSignatureFilter.VERIFIED_USER_ID) String userId) {
    JobDetailsResponse job =
        jobService.getJobDetails(jobId, new Caller(userId, requestUuid, authorization));

    return ResponseEntity
        .status(HttpStatus.OK)
        .body(new GlobalRestResponse<>(true, job));
  }

  @Override
  @PatchMapping("/{jobId}/increase-seen")
  public ResponseEntity<GlobalRestResponse<Long>> increaseSeen(
      @PathVariable String jobId,
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid,
      @RequestHeader(AUTHORIZATION_HEADER) String authorization,
      @RequestAttribute(JwtSignatureFilter.VERIFIED_USER_ID) String userId) {
    Long totalSeen =
        jobService.increaseSeen(jobId, new Caller(userId, requestUuid, authorization));

    return ResponseEntity
        .status(HttpStatus.OK)
        .body(new GlobalRestResponse<>(true, totalSeen));
  }

  @Override
  @PatchMapping("/{jobId}/refresh")
  public ResponseEntity<GlobalRestResponse<RefreshJobResponse>> refreshJob(
      @PathVariable String jobId,
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid,
      @RequestHeader(AUTHORIZATION_HEADER) String authorization,
      @RequestAttribute(JwtSignatureFilter.VERIFIED_USER_ID) String userId) {
    RefreshJobResponse refreshed =
        jobService.refreshJob(jobId, new Caller(userId, requestUuid, authorization));

    return ResponseEntity
        .status(HttpStatus.OK)
        .body(new GlobalRestResponse<>(true, refreshed));
  }

  @Override
  @PatchMapping("/{jobId}/close")
  public ResponseEntity<GlobalRestResponse<CloseJobResponse>> closeJob(
      @PathVariable String jobId,
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid,
      @RequestHeader(AUTHORIZATION_HEADER) String authorization,
      @RequestAttribute(JwtSignatureFilter.VERIFIED_USER_ID) String userId) {
    CloseJobResponse closed =
        jobService.closeJob(jobId, new Caller(userId, requestUuid, authorization));

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
      @RequestHeader(AUTHORIZATION_HEADER) String authorization,
      @RequestAttribute(JwtSignatureFilter.VERIFIED_USER_ID) String userId) {
    ApplyJobResponse applied =
        jobService.applyToJob(jobId, request, new Caller(userId, requestUuid, authorization));

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
      @RequestHeader(AUTHORIZATION_HEADER) String authorization,
      @RequestAttribute(JwtSignatureFilter.VERIFIED_USER_ID) String userId) {
    PagedResponse<JobCandidateResponse> candidates = jobService.getCandidates(
        jobId, page, pageSize, new Caller(userId, requestUuid, authorization));

    return ResponseEntity
        .status(HttpStatus.OK)
        .body(new GlobalRestResponse<>(true, candidates));
  }

  @Override
  @GetMapping("/{jobId}/candidate/{candidateId}/candidate-explanation")
  public ResponseEntity<GlobalRestResponse<CandidateExplanationResponse>> explainCandidate(
      @PathVariable String jobId,
      @PathVariable String candidateId,
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid,
      @RequestHeader(AUTHORIZATION_HEADER) String authorization,
      @RequestAttribute(JwtSignatureFilter.VERIFIED_USER_ID) String userId) {
    CandidateExplanationResponse explanation = jobService.explainCandidate(
        jobId, candidateId, new Caller(userId, requestUuid, authorization));

    return ResponseEntity
        .status(HttpStatus.OK)
        .body(new GlobalRestResponse<>(true, explanation));
  }

  @Override
  @GetMapping("/{jobId}/upskilling-suggestion")
  public ResponseEntity<GlobalRestResponse<UpskillingSuggestionResponse>> suggestUpskilling(
      @PathVariable String jobId,
      @RequestParam(defaultValue = "en") String language,
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid,
      @RequestHeader(AUTHORIZATION_HEADER) String authorization,
      @RequestAttribute(JwtSignatureFilter.VERIFIED_USER_ID) String userId) {
    UpskillingSuggestionResponse suggestion = jobService.suggestUpskilling(
        jobId, language, new Caller(userId, requestUuid, authorization));

    return ResponseEntity
        .status(HttpStatus.OK)
        .body(new GlobalRestResponse<>(true, suggestion));
  }
}
