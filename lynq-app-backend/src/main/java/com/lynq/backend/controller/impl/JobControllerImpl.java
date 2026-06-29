package com.lynq.backend.controller.impl;

import com.lynq.backend.controller.JobController;
import com.lynq.backend.controller.request.CreateJobRequest;
import com.lynq.backend.controller.response.CreateJobRestResponse;
import com.lynq.backend.controller.response.GlobalRestResponse;
import com.lynq.backend.model.JobPostEntity;
import com.lynq.backend.model.JobPostSkillEntity;
import com.lynq.backend.service.JobService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
        .companyId(job.getCompany().getId())
        .createdByUserId(job.getCreatedByUser().getId())
        .skills(job.getSkills().stream().map(JobPostSkillEntity::getSkill).toList())
        .build();

    return ResponseEntity
        .status(HttpStatus.CREATED)
        .body(new GlobalRestResponse<>(true, response));
  }

}