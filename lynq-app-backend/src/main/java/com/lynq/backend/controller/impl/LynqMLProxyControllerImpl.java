package com.lynq.backend.controller.impl;

import com.lynq.backend.aspect.AuditLog;
import com.lynq.backend.client.response.CandidateExplanationResponse;
import com.lynq.backend.client.response.SkillEnhanceResponse;
import com.lynq.backend.client.response.UpskillingSuggestionResponse;
import com.lynq.backend.controller.LynqMLProxyController;
import com.lynq.backend.controller.request.CandidateEvaluationRequest;
import com.lynq.backend.controller.request.SkillEnhanceRequest;
import com.lynq.backend.controller.response.GlobalRestResponse;
import com.lynq.backend.service.LynqMLProxyService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/ml")
@Validated
public class LynqMLProxyControllerImpl implements LynqMLProxyController {

  private static final String REQUEST_UUID_HEADER = "lynq-request-uuid";

  private final LynqMLProxyService lynqMLProxyService;

  public LynqMLProxyControllerImpl(LynqMLProxyService lynqMLProxyService) {
    this.lynqMLProxyService = lynqMLProxyService;
  }

  @Override
  @PostMapping("/skill-enhance")
  @AuditLog
  public ResponseEntity<GlobalRestResponse<SkillEnhanceResponse>> enhanceSkills(
      @RequestBody SkillEnhanceRequest request,
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid) {
    SkillEnhanceResponse response = lynqMLProxyService.enhanceSkills(
        request.getTitle(),
        request.getDescription(),
        request.getWorkType(),
        requestUuid);

    return ResponseEntity
        .status(HttpStatus.OK)
        .body(new GlobalRestResponse<>(true, response));
  }

  @Override
  @PostMapping("/upskilling-suggestion")
  @AuditLog
  public ResponseEntity<GlobalRestResponse<UpskillingSuggestionResponse>> suggestUpskilling(
      @RequestBody CandidateEvaluationRequest request,
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid) {
    UpskillingSuggestionResponse response =
        lynqMLProxyService.suggestUpskilling(request, requestUuid);

    return ResponseEntity
        .status(HttpStatus.OK)
        .body(new GlobalRestResponse<>(true, response));
  }

  @Override
  @PostMapping("/candidate-explanation")
  @AuditLog
  public ResponseEntity<GlobalRestResponse<CandidateExplanationResponse>> explainCandidate(
      @RequestBody CandidateEvaluationRequest request,
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid) {
    CandidateExplanationResponse response =
        lynqMLProxyService.explainCandidate(request, requestUuid);

    return ResponseEntity
        .status(HttpStatus.OK)
        .body(new GlobalRestResponse<>(true, response));
  }
}