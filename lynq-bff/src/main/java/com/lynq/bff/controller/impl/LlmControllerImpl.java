package com.lynq.bff.controller.impl;

import com.lynq.bff.client.request.LanguageDetectionRequest;
import com.lynq.bff.client.request.SkillEnhanceRequest;
import com.lynq.bff.client.request.TranslateResumeRequest;
import com.lynq.bff.client.response.LanguageDetectionResponse;
import com.lynq.bff.client.response.SkillEnhanceResponse;
import com.lynq.bff.client.response.SkillExtractionResponse;
import com.lynq.bff.controller.LlmController;
import com.lynq.bff.controller.response.GlobalRestResponse;
import com.lynq.bff.exceptions.ForbiddenException;
import com.lynq.bff.ratelimit.RateLimitTier;
import com.lynq.bff.ratelimit.RateLimited;
import com.lynq.bff.security.LynqUserPrincipal;
import com.lynq.bff.service.Caller;
import com.lynq.bff.service.LlmService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class LlmControllerImpl implements LlmController {

  private static final String REQUEST_UUID_HEADER = "lynq-request-uuid";

  private static final Set<String> EVALUATIONS_OWNED_BY_BACKEND =
      Set.of("/upskilling_suggestion", "/candidate-explanation");

  private static final String USE_BACKEND_JOB_ENDPOINTS_INSTEAD =
      "This lynq-llm evaluation is built from lynq-backend's data and must be reached through its "
          + "job endpoints, not relayed from the browser";

  private static final String URL_TAKING_ENDPOINT_NOT_RELAYED =
      "This lynq-llm endpoint fetches a caller-supplied URL server-side and is not reachable "
          + "through the gateway; the resume preview is driven by POST /resume/preview, which "
          + "signs those URLs itself";

  private final LlmService llmService;

  public LlmControllerImpl(LlmService llmService) {
    this.llmService = llmService;
  }

  @Override
  @PostMapping("/skill-enhance")
  @RateLimited(RateLimitTier.STANDARD)
  public ResponseEntity<GlobalRestResponse<SkillEnhanceResponse>> enhanceSkills(
      @RequestBody SkillEnhanceRequest request,
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid,
      @AuthenticationPrincipal LynqUserPrincipal principal) {
    SkillEnhanceResponse skills =
        llmService.enhanceSkills(request, caller(principal, requestUuid));

    return ResponseEntity
        .status(HttpStatus.OK)
        .body(new GlobalRestResponse<>(true, skills));
  }

  @Override
  @PostMapping("/translate")
  @RateLimited(RateLimitTier.HEAVY)
  public ResponseEntity<GlobalRestResponse<Object>> translateResume(
      @RequestBody TranslateResumeRequest request,
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid,
      @AuthenticationPrincipal LynqUserPrincipal principal) {
    Object translated =
        llmService.translateResume(request, caller(principal, requestUuid));

    return ResponseEntity
        .status(HttpStatus.OK)
        .body(new GlobalRestResponse<>(true, translated));
  }

  @Override
  @PostMapping("/detect-language")
  @RateLimited(RateLimitTier.LIGHT)
  public ResponseEntity<GlobalRestResponse<LanguageDetectionResponse>> detectLanguage(
      @RequestBody LanguageDetectionRequest request,
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid,
      @AuthenticationPrincipal LynqUserPrincipal principal) {
    LanguageDetectionResponse detected =
        llmService.detectLanguage(request, caller(principal, requestUuid));

    return ResponseEntity
        .status(HttpStatus.OK)
        .body(new GlobalRestResponse<>(true, detected));
  }

  @Override
  @PostMapping("/resume/skill-extraction")
  @RateLimited(RateLimitTier.STANDARD)
  public ResponseEntity<GlobalRestResponse<SkillExtractionResponse>> extractResumeSkills(
      @RequestBody Object resume,
      @RequestParam(required = false) String language,
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid,
      @AuthenticationPrincipal LynqUserPrincipal principal) {
    SkillExtractionResponse skills = llmService.extractResumeSkills(
        resume, language, caller(principal, requestUuid));

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

  private static Caller caller(LynqUserPrincipal principal, String requestUuid) {
    return new Caller(principal.getId(), requestUuid, principal.getAuthorization());
  }
}
