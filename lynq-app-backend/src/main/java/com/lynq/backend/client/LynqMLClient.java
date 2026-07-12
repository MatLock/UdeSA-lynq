package com.lynq.backend.client;

import com.lynq.backend.client.request.SkillEnhanceRequest;
import com.lynq.backend.client.response.SkillEnhanceResponse;
import com.lynq.backend.controller.response.GlobalRestResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

/**
 * HTTP client for the lynq-ml service. The {@code lynq-request-uuid} header is
 * forwarded so logs can be correlated across services, and {@code user-id} /
 * {@code company-id} identify who the ML request is being made on behalf of.
 */
@FeignClient(name = "lynqMl", url = "${lynq.ml.url}")
public interface LynqMLClient {

  String REQUEST_UUID_HEADER = "lynq-request-uuid";
  String USER_ID_HEADER = "user-id";
  String COMPANY_ID_HEADER = "company-id";

  @PostMapping("/skill-enhance")
  GlobalRestResponse<SkillEnhanceResponse> enhanceSkills(
      @RequestBody SkillEnhanceRequest request,
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid,
      @RequestHeader(USER_ID_HEADER) String userId,
      @RequestHeader(COMPANY_ID_HEADER) String companyId);
}