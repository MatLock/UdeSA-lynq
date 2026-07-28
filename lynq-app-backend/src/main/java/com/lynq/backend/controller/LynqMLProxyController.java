package com.lynq.backend.controller;

import com.lynq.backend.client.response.SkillEnhanceResponse;
import com.lynq.backend.client.response.SkillExtractionResponse;
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
      summary = "Extract the skills from a resume",
      description = "Forwards the structured resume payload to the lynq-ml service, which "
          + "consolidates all of its skills into technical/tools/soft buckets via an LLM. The "
          + "authenticated user must be a CANDIDATE-type user; the request is enriched with the "
          + "caller's user id, the request uuid and the caller's UI language before being sent to "
          + "lynq-ml. The soft skills come back written in that language (technical and tool names "
          + "are always kept verbatim), defaulting to English when 'language' is omitted.",
      security = @SecurityRequirement(name = "bearerAuth"))
  ResponseEntity<GlobalRestResponse<SkillExtractionResponse>> extractResumeSkills(
      Object resumePayload, String requestUuid, String language);
}
