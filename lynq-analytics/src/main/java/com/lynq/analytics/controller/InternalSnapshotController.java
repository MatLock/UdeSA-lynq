package com.lynq.analytics.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "Internal", description = "Service-to-service operations, not reachable from the DMZ")
public interface InternalSnapshotController {

  @Operation(
      summary = "Take the daily snapshot",
      description = "Starts the daily snapshot in the background and answers 202 with no body: "
          + "the tag frequency is recomputed, the daily aggregates of the open job posts are "
          + "written, and every candidate's market fit and position among similar candidates is "
          + "scored against lynq-app-backend and written. Each step replaces the rows of today, "
          + "so running it again the same day gives the same result. Fails with 409 while a "
          + "snapshot is already running. Meant for the Kubernetes CronJob: authenticated with "
          + "the shared internal token header, not a bearer token, and logged under the "
          + "lynq-request-uuid of the trigger.")
  ResponseEntity<Void> takeSnapshot(@Parameter(hidden = true) String requestUuid);
}
