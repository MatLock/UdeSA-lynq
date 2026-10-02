package com.lynq.analytics.controller.impl;

import com.lynq.analytics.aspect.AuditLog;
import com.lynq.analytics.controller.AnalyticsController;
import com.lynq.analytics.controller.response.GlobalRestResponse;
import com.lynq.analytics.security.HasRole;
import com.lynq.analytics.security.Role;
import com.lynq.analytics.service.StandingService;
import com.lynq.analytics.stats.Standing;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/dmz/analytics")
public class AnalyticsControllerImpl implements AnalyticsController {

  private final StandingService standingService;

  public AnalyticsControllerImpl(StandingService standingService) {
    this.standingService = standingService;
  }

  @Override
  @GetMapping("/job/{jobId}/standing")
  @HasRole(Role.CANDIDATE)
  @AuditLog
  public ResponseEntity<GlobalRestResponse<Standing>> getStanding(
      @PathVariable String jobId,
      @AuthenticationPrincipal(expression = "id") String userId) {
    Standing standing = standingService.standing(jobId, userId);

    return ResponseEntity
        .status(HttpStatus.OK)
        .body(new GlobalRestResponse<>(true, standing));
  }
}
