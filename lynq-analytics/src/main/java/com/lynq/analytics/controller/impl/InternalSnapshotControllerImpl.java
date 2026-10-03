package com.lynq.analytics.controller.impl;

import com.lynq.analytics.aspect.AuditLog;
import com.lynq.analytics.controller.InternalSnapshotController;
import com.lynq.analytics.exceptions.ConflictException;
import com.lynq.analytics.service.DailySnapshotService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/snapshot")
public class InternalSnapshotControllerImpl implements InternalSnapshotController {

  private static final String REQUEST_UUID_HEADER = "lynq-request-uuid";
  private static final String ALREADY_RUNNING = "A daily snapshot is already running";

  private final DailySnapshotService dailySnapshotService;

  public InternalSnapshotControllerImpl(DailySnapshotService dailySnapshotService) {
    this.dailySnapshotService = dailySnapshotService;
  }

  @Override
  @PostMapping
  @AuditLog
  public ResponseEntity<Void> takeSnapshot(
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid) {
    if (!dailySnapshotService.start(requestUuid)) {
      throw new ConflictException(ALREADY_RUNNING);
    }
    return ResponseEntity.accepted().build();
  }
}
