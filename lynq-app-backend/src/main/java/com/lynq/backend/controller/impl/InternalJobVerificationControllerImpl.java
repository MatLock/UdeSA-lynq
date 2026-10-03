package com.lynq.backend.controller.impl;

import com.lynq.backend.aspect.AuditLog;
import com.lynq.backend.controller.InternalJobVerificationController;
import com.lynq.backend.controller.request.LivenessReportsRequest;
import com.lynq.backend.controller.response.ExpireJobPostsRestResponse;
import com.lynq.backend.controller.response.GlobalRestResponse;
import com.lynq.backend.controller.response.LivenessRestResponse;
import com.lynq.backend.controller.response.VerificationCandidatesRestResponse;
import com.lynq.backend.service.JobVerificationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/job-posts")
@Validated
public class InternalJobVerificationControllerImpl implements InternalJobVerificationController {

  private final JobVerificationService jobVerificationService;

  public InternalJobVerificationControllerImpl(JobVerificationService jobVerificationService) {
    this.jobVerificationService = jobVerificationService;
  }

  @Override
  @GetMapping("/verification-candidates")
  @AuditLog
  public ResponseEntity<GlobalRestResponse<VerificationCandidatesRestResponse>> candidates() {
    return ResponseEntity.ok(new GlobalRestResponse<>(true, jobVerificationService.candidates()));
  }

  @Override
  @PostMapping("/liveness")
  @AuditLog
  public ResponseEntity<GlobalRestResponse<LivenessRestResponse>> liveness(
      @Valid @RequestBody LivenessReportsRequest request) {
    LivenessRestResponse response = jobVerificationService.report(request.getReports());
    return ResponseEntity.ok(new GlobalRestResponse<>(true, response));
  }

  @Override
  @PostMapping("/expire")
  @AuditLog
  public ResponseEntity<GlobalRestResponse<ExpireJobPostsRestResponse>> expire() {
    return ResponseEntity.ok(new GlobalRestResponse<>(true, jobVerificationService.expire()));
  }
}
