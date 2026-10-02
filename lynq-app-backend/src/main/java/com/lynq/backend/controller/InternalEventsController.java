package com.lynq.backend.controller;

import com.lynq.backend.controller.response.GlobalRestResponse;
import com.lynq.backend.controller.response.ReplayEventsRestResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "Internal", description = "Service-to-service operations, not reachable from the DMZ")
public interface InternalEventsController {

  @Operation(
      summary = "Replay the domain events of the current state",
      description = "Publishes to lynq-domain-events the events that describe what the database "
          + "holds now: every job post published, and closed if it is, every candidate's skills "
          + "and expected salary, and every application with its score. Each event carries the "
          + "date stored with the fact and a deterministic UUIDv5 event id, so running it again "
          + "repeats the same ids and the consumers discard them. Authenticated with the shared "
          + "internal token header.")
  ResponseEntity<GlobalRestResponse<ReplayEventsRestResponse>> replay();
}
