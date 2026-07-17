package com.lynq.backend.controller.impl;

import com.lynq.backend.aspect.AuditLog;
import com.lynq.backend.controller.JobController;
import com.lynq.backend.controller.request.CreateJobRequest;
import com.lynq.backend.controller.response.ApplyJobRestResponse;
import com.lynq.backend.controller.response.CreateJobRestResponse;
import com.lynq.backend.controller.response.GetJobRestResponse;
import com.lynq.backend.controller.response.GlobalRestResponse;
import com.lynq.backend.controller.response.JobCandidateResponse;
import com.lynq.backend.controller.response.PagedRestResponse;
import com.lynq.backend.model.JobPostEntity;
import com.lynq.backend.model.JobPostSkillEntity;
import com.lynq.backend.model.UserApplicationJobEntity;
import com.lynq.backend.service.JobFilter;
import com.lynq.backend.service.JobService;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/job")
@Validated
public class JobControllerImpl implements JobController {

  private final JobService jobService;

  public JobControllerImpl(JobService jobService) {
    this.jobService = jobService;
  }

  @Override
  @PostMapping
  @AuditLog
  public ResponseEntity<GlobalRestResponse<CreateJobRestResponse>> createJob(@RequestBody CreateJobRequest request) {
    JobPostEntity job = jobService.createJob(
        request.getTitle(),
        request.getDescription(),
        request.getWorkType(),
        request.getSalaryRangeDown(),
        request.getSalaryRangeTop(),
        request.getJobPostSource(),
        request.getSkills());

    CreateJobRestResponse response = CreateJobRestResponse.builder()
        .jobId(job.getId())
        .title(job.getTitle())
        .description(job.getDescription())
        .workType(job.getWorkType())
        .salaryRangeDown(job.getSalaryRangeDown())
        .salaryRangeTop(job.getSalaryRangeTop())
        .jobPostSource(job.getJobPostSource())
        .createdOn(job.getCreatedOn())
        .totalSeen(job.getTotalSeen())
        .companyId(job.getCompany().getId())
        .createdByUserId(job.getCreatedByUser().getId())
        .skills(job.getSkills().stream().map(JobPostSkillEntity::getSkill).toList())
        .build();

    return ResponseEntity
        .status(HttpStatus.CREATED)
        .body(new GlobalRestResponse<>(true, response));
  }

  @Override
  @GetMapping
  @AuditLog
  public ResponseEntity<GlobalRestResponse<PagedRestResponse<GetJobRestResponse>>> getJobs(
      @RequestParam(defaultValue = "0") Integer page,
      @RequestParam(defaultValue = "20") Integer size,
      @RequestParam(required = false) String filterValue) {
    JobFilter filter = new JobFilter(filterValue);
    PagedRestResponse<GetJobRestResponse> jobs =
        jobService.searchAvailableJobs(filter, PageRequest.of(page, size));

    return ResponseEntity
        .status(HttpStatus.OK)
        .body(new GlobalRestResponse<>(true, jobs));
  }

  @Override
  @PatchMapping("/{jobId}/increase-seen")
  @AuditLog
  public ResponseEntity<GlobalRestResponse<Long>> increaseSeen(@PathVariable String jobId) {
    JobPostEntity job = jobService.increaseSeen(jobId);

    return ResponseEntity
        .status(HttpStatus.OK)
        .body(new GlobalRestResponse<>(true, job.getTotalSeen()));
  }

  @Override
  @PostMapping("/{jobId}/apply")
  @AuditLog
  public ResponseEntity<GlobalRestResponse<ApplyJobRestResponse>> applyToJob(
      @PathVariable String jobId) {
    UserApplicationJobEntity application = jobService.applyToJob(jobId);

    ApplyJobRestResponse response = ApplyJobRestResponse.builder()
        .applicationId(application.getId())
        .jobId(application.getJobPost().getId())
        .userId(application.getUser().getId())
        .appliedOn(application.getAppliedOn())
        .build();

    return ResponseEntity
        .status(HttpStatus.CREATED)
        .body(new GlobalRestResponse<>(true, response));
  }

  @Override
  @GetMapping("/{jobId}/candidates")
  @AuditLog
  public ResponseEntity<GlobalRestResponse<PagedRestResponse<JobCandidateResponse>>> getJobCandidates(
      @PathVariable String jobId,
      @RequestParam(defaultValue = "0") Integer page,
      @RequestParam(defaultValue = "10") Integer pageSize) {
    PagedRestResponse<JobCandidateResponse> candidates =
        jobService.getJobCandidates(jobId, PageRequest.of(page, pageSize));

    return ResponseEntity
        .status(HttpStatus.OK)
        .body(new GlobalRestResponse<>(true, candidates));
  }

}