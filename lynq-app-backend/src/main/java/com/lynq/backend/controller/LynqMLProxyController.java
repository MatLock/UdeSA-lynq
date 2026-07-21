package com.lynq.backend.controller;

import com.lynq.backend.client.response.CandidateExplanationResponse;
import com.lynq.backend.client.response.SkillEnhanceResponse;
import com.lynq.backend.client.response.UpskillingSuggestionResponse;
import com.lynq.backend.controller.request.CandidateEvaluationRequest;
import com.lynq.backend.controller.request.SkillEnhanceRequest;
import com.lynq.backend.controller.response.GlobalRestResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;

@Tag(name = "Lynq ML", description = "Proxy operations that forward to the lynq-ml service")
public interface LynqMLProxyController {

  @Operation(
      summary = "Enhance skills for a job post",
      description = "Forwards the job post to the lynq-ml service, which extracts the key technical "
          + "skills via an LLM. The authenticated user must be a COMPANY-type user linked to a "
          + "company; the request is enriched with the caller's user id, company id and the request "
          + "uuid before being sent to lynq-ml.",
      security = @SecurityRequirement(name = "bearerAuth"))
  ResponseEntity<GlobalRestResponse<SkillEnhanceResponse>> enhanceSkills(
      @Valid SkillEnhanceRequest request, String requestUuid);

  @Operation(
      summary = "Suggest upskilling courses for a candidate",
      description = "Forwards a job + candidate pair to the lynq-ml service, which assesses the gap "
          + "and returns a recruiter verdict plus upskilling course links. The authenticated user "
          + "must be a COMPANY-type user linked to a company; the request is enriched with the "
          + "caller's user id, company id and the request uuid before being sent to lynq-ml.",
      security = @SecurityRequirement(name = "bearerAuth"))
  ResponseEntity<GlobalRestResponse<UpskillingSuggestionResponse>> suggestUpskilling(
      @Valid CandidateEvaluationRequest request, String requestUuid);

  @Operation(
      summary = "Explain whether a candidate should be hired",
      description = "Forwards a job + candidate pair to the lynq-ml service, which returns a hiring "
          + "recommendation with the reasons for and against hiring the candidate. The "
          + "authenticated user must be a COMPANY-type user linked to a company; the request is "
          + "enriched with the caller's user id, company id and the request uuid before being sent "
          + "to lynq-ml.",
      security = @SecurityRequirement(name = "bearerAuth"))
  ResponseEntity<GlobalRestResponse<CandidateExplanationResponse>> explainCandidate(
      @Valid CandidateEvaluationRequest request, String requestUuid);
}