package com.lynq.bff.controller.impl;

import com.lynq.bff.client.request.LanguageDetectionRequest;
import com.lynq.bff.client.request.SkillEnhanceRequest;
import com.lynq.bff.client.request.TranslateResumeRequest;
import com.lynq.bff.client.response.LanguageDetectionResponse;
import com.lynq.bff.client.response.SkillEnhanceResponse;
import com.lynq.bff.client.response.SkillExtractionResponse;
import com.lynq.bff.controller.MlController;
import com.lynq.bff.controller.response.GlobalRestResponse;
import com.lynq.bff.exceptions.ForbiddenException;
import com.lynq.bff.filter.JwtSignatureFilter;
import com.lynq.bff.service.Caller;
import com.lynq.bff.service.MlService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MlControllerImpl implements MlController {

  private static final String REQUEST_UUID_HEADER = "lynq-request-uuid";

  private static final Set<String> EVALUATIONS_OWNED_BY_BACKEND =
      Set.of("/upskilling_suggestion", "/candidate-explanation");

  private static final String USE_BACKEND_JOB_ENDPOINTS_INSTEAD =
      "This lynq-ml evaluation is built from lynq-backend's data and must be reached through its "
          + "job endpoints, not relayed from the browser";

  private static final String URL_TAKING_ENDPOINT_NOT_RELAYED =
      "This lynq-ml endpoint fetches a caller-supplied URL server-side and is not reachable "
          + "through the gateway; the resume preview is driven by POST /resume/preview, which "
          + "signs those URLs itself";

  private final MlService mlService;

  public MlControllerImpl(MlService mlService) {
    this.mlService = mlService;
  }

  @Override
  @PostMapping("/skill-enhance")
  public ResponseEntity<GlobalRestResponse<SkillEnhanceResponse>> enhanceSkills(
      @RequestBody SkillEnhanceRequest request,
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid,
      @RequestAttribute(JwtSignatureFilter.VERIFIED_USER_ID) String userId) {
    SkillEnhanceResponse skills =
        mlService.enhanceSkills(request, new Caller(userId, requestUuid, null));

    return ResponseEntity
        .status(HttpStatus.OK)
        .body(new GlobalRestResponse<>(true, skills));
  }

  @Override
  @PostMapping("/translate")
  public ResponseEntity<GlobalRestResponse<Object>> translateResume(
      @RequestBody TranslateResumeRequest request,
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid,
      @RequestAttribute(JwtSignatureFilter.VERIFIED_USER_ID) String userId) {
    Object translated =
        mlService.translateResume(request, new Caller(userId, requestUuid, null));

    return ResponseEntity
        .status(HttpStatus.OK)
        .body(new GlobalRestResponse<>(true, translated));
  }

  @Override
  @PostMapping("/detect-language")
  public ResponseEntity<GlobalRestResponse<LanguageDetectionResponse>> detectLanguage(
      @RequestBody LanguageDetectionRequest request,
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid,
      @RequestAttribute(JwtSignatureFilter.VERIFIED_USER_ID) String userId) {
    LanguageDetectionResponse detected =
        mlService.detectLanguage(request, new Caller(userId, requestUuid, null));

    return ResponseEntity
        .status(HttpStatus.OK)
        .body(new GlobalRestResponse<>(true, detected));
  }

  @Override
  @PostMapping("/resume/skill-extraction")
  public ResponseEntity<GlobalRestResponse<SkillExtractionResponse>> extractResumeSkills(
      @RequestBody Object resume,
      @RequestParam(required = false) String language,
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid,
      @RequestAttribute(JwtSignatureFilter.VERIFIED_USER_ID) String userId) {
    SkillExtractionResponse skills = mlService.extractResumeSkills(
        resume, language, new Caller(userId, requestUuid, null));

    return ResponseEntity
        .status(HttpStatus.OK)
        .body(new GlobalRestResponse<>(true, skills));
  }

  @Override
  @RequestMapping({"/upskilling_suggestion", "/candidate-explanation",
      "/parse-resume", "/resume-template-creation"})
  public ResponseEntity<Void> refuseUnrelayedEndpoint(HttpServletRequest request) {
    throw new ForbiddenException(
        EVALUATIONS_OWNED_BY_BACKEND.contains(request.getServletPath())
            ? USE_BACKEND_JOB_ENDPOINTS_INSTEAD
            : URL_TAKING_ENDPOINT_NOT_RELAYED);
  }
}
