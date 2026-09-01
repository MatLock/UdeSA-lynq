package com.lynq.backend.client;

import com.lynq.backend.client.request.CandidateEvaluationRequest;
import com.lynq.backend.client.response.CandidateExplanationResponse;
import com.lynq.backend.client.response.UpskillingSuggestionResponse;
import com.lynq.backend.controller.response.GlobalRestResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "lynqMl", url = "${lynq.ml.url}")
public interface LynqMLClient {

  String REQUEST_UUID_HEADER = "lynq-request-uuid";
  String USER_ID_HEADER = "user-id";
  String COMPANY_ID_HEADER = "company-id";
  String OUTPUT_LANGUAGE_HEADER = "output-language";

  @PostMapping("/dmz/upskilling_suggestion")
  GlobalRestResponse<UpskillingSuggestionResponse> upskillingSuggestion(
      @RequestBody CandidateEvaluationRequest request,
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid,
      @RequestHeader(USER_ID_HEADER) String userId,
      @RequestHeader(COMPANY_ID_HEADER) String companyId,
      @RequestHeader(OUTPUT_LANGUAGE_HEADER) String outputLanguage);

  @PostMapping("/dmz/candidate-explanation")
  GlobalRestResponse<CandidateExplanationResponse> candidateExplanation(
      @RequestBody CandidateEvaluationRequest request,
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid,
      @RequestHeader(USER_ID_HEADER) String userId,
      @RequestHeader(COMPANY_ID_HEADER) String companyId);
}