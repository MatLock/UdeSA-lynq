package com.lynq.bff.controller;

import com.lynq.bff.client.request.LanguageDetectionRequest;
import com.lynq.bff.client.request.SkillEnhanceRequest;
import com.lynq.bff.client.request.TranslateResumeRequest;
import com.lynq.bff.client.response.LanguageDetectionResponse;
import com.lynq.bff.client.response.SkillEnhanceResponse;
import com.lynq.bff.client.response.SkillExtractionResponse;
import com.lynq.bff.controller.response.GlobalRestResponse;
import com.lynq.bff.security.LynqUserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;

@ApiResponses({
    @ApiResponse(responseCode = "401", description = "The Authorization header is missing, or the "
        + "access token's signature is invalid or expired."),
    @ApiResponse(responseCode = "403", description = "The lynq-request-uuid header is missing."),
    @ApiResponse(responseCode = "502", description = "lynq-ml could not be reached, or the model "
        + "it called failed.")
})
public interface MlController {

  @Operation(
      summary = "Extract the key skills of a job posting",
      description = "Turns text the caller already has into a result, so there is nothing for "
          + "lynq-app-backend to add.")
  ResponseEntity<GlobalRestResponse<SkillEnhanceResponse>> enhanceSkills(
      SkillEnhanceRequest request,
      @Parameter(hidden = true) String requestUuid,
      @Parameter(hidden = true) LynqUserPrincipal principal);

  @Operation(summary = "Translate a resume into another language")
  ResponseEntity<GlobalRestResponse<Object>> translateResume(
      TranslateResumeRequest request,
      @Parameter(hidden = true) String requestUuid,
      @Parameter(hidden = true) LynqUserPrincipal principal);

  @Operation(summary = "Detect the main language of a text")
  ResponseEntity<GlobalRestResponse<LanguageDetectionResponse>> detectLanguage(
      LanguageDetectionRequest request,
      @Parameter(hidden = true) String requestUuid,
      @Parameter(hidden = true) LynqUserPrincipal principal);

  @Operation(summary = "Bucket a resume's skills and derive its capability tags")
  ResponseEntity<GlobalRestResponse<SkillExtractionResponse>> extractResumeSkills(
      Object resume,
      String language,
      @Parameter(hidden = true) String requestUuid,
      @Parameter(hidden = true) LynqUserPrincipal principal);

  @Operation(
      summary = "Refuse a lynq-ml endpoint the gateway does not relay",
      description = "`upskilling_suggestion` and `candidate-explanation` are built from "
          + "lynq-app-backend's data and are reached through its job endpoints. `parse-resume` and "
          + "`resume-template-creation` fetch a caller-supplied URL server-side, which would make "
          + "the gateway an SSRF vector — `resume-template-creation` is driven by "
          + "`POST /resume/preview` instead, which signs those URLs itself. These are mapped only "
          + "so the refusal can say why; any other lynq-ml endpoint is a plain 404.")
  @ApiResponses(
      @ApiResponse(responseCode = "403", description = "Always. The reason names the route to use."))
  ResponseEntity<Void> refuseUnrelayedEndpoint(
      @Parameter(hidden = true) HttpServletRequest request);
}
