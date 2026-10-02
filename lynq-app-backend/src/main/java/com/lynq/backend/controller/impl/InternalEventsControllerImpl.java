package com.lynq.backend.controller.impl;

import com.lynq.backend.aspect.AuditLog;
import com.lynq.backend.controller.InternalEventsController;
import com.lynq.backend.controller.response.GlobalRestResponse;
import com.lynq.backend.controller.response.ReplayEventsRestResponse;
import com.lynq.backend.service.DomainEventReplayService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/events")
public class InternalEventsControllerImpl implements InternalEventsController {

  private final DomainEventReplayService domainEventReplayService;

  public InternalEventsControllerImpl(DomainEventReplayService domainEventReplayService) {
    this.domainEventReplayService = domainEventReplayService;
  }

  @Override
  @PostMapping("/replay")
  @AuditLog
  public ResponseEntity<GlobalRestResponse<ReplayEventsRestResponse>> replay() {
    return ResponseEntity.ok(new GlobalRestResponse<>(true, domainEventReplayService.replay()));
  }
}
