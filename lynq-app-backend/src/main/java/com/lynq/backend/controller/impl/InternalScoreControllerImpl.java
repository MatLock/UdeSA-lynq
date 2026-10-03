package com.lynq.backend.controller.impl;

import com.lynq.backend.controller.InternalScoreController;
import com.lynq.backend.controller.request.ScoreBatchRequest;
import com.lynq.backend.controller.response.GlobalRestResponse;
import com.lynq.backend.controller.response.ScoreBatchRestResponse;
import com.lynq.backend.service.ScoreBatchService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/score")
@Validated
public class InternalScoreControllerImpl implements InternalScoreController {

  private final ScoreBatchService scoreBatchService;

  public InternalScoreControllerImpl(ScoreBatchService scoreBatchService) {
    this.scoreBatchService = scoreBatchService;
  }

  @Override
  @PostMapping("/batch")
  public ResponseEntity<GlobalRestResponse<ScoreBatchRestResponse>> scoreBatch(
      @Valid @RequestBody ScoreBatchRequest request) {
    return ResponseEntity.ok(new GlobalRestResponse<>(true, scoreBatchService.score(request)));
  }
}
