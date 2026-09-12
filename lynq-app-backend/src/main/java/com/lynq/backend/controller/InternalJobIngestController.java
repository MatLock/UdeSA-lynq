package com.lynq.backend.controller;

import com.lynq.backend.controller.request.IngestJobPostsRequest;
import com.lynq.backend.controller.response.GlobalRestResponse;
import com.lynq.backend.controller.response.IngestJobPostsRestResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;

@Tag(name = "Internal", description = "Service-to-service operations, not reachable from the DMZ")
public interface InternalJobIngestController {

  @Operation(
      summary = "Ingest a batch of scraped job posts",
      description = "Upserts a batch of job posts scraped by lynq-feeders, together with their "
          + "companies, skills and similarity tags. Each row's id is a deterministic UUIDv5 "
          + "derived from the source and the portal's listing id, so re-running the same feed "
          + "updates the rows in place instead of duplicating them. Job posts ingested here have "
          + "no creating user. Authenticated with the shared internal token header, not a bearer "
          + "token: the caller is a cron job with no user behind it.")
  ResponseEntity<GlobalRestResponse<IngestJobPostsRestResponse>> ingest(
      @Valid IngestJobPostsRequest request);
}
