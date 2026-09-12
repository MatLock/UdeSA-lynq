package com.lynq.backend.controller.impl;

import com.lynq.backend.aspect.AuditLog;
import com.lynq.backend.controller.InternalJobIngestController;
import com.lynq.backend.controller.request.IngestJobPostsRequest;
import com.lynq.backend.controller.response.GlobalRestResponse;
import com.lynq.backend.controller.response.IngestJobPostsRestResponse;
import com.lynq.backend.service.JobIngestService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/job-posts")
@Validated
public class InternalJobIngestControllerImpl implements InternalJobIngestController {

  private final JobIngestService jobIngestService;

  public InternalJobIngestControllerImpl(JobIngestService jobIngestService) {
    this.jobIngestService = jobIngestService;
  }

  @Override
  @PostMapping("/ingest")
  @AuditLog
  public ResponseEntity<GlobalRestResponse<IngestJobPostsRestResponse>> ingest(
      @Valid @RequestBody IngestJobPostsRequest request) {
    IngestJobPostsRestResponse response = jobIngestService.ingest(request.getJobPosts());
    return ResponseEntity.ok(new GlobalRestResponse<>(true, response));
  }
}
