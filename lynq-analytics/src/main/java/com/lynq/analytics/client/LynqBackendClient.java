package com.lynq.analytics.client;

import com.lynq.analytics.client.request.ScoreBatchRequest;
import com.lynq.analytics.client.response.ScoreBatchResponse;
import com.lynq.analytics.controller.response.GlobalRestResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "lynqBackend", url = "${lynq.backend.url}")
public interface LynqBackendClient {

  String INTERNAL_TOKEN_HEADER = "lynq-internal-token";
  String REQUEST_UUID_HEADER = "lynq-request-uuid";

  @PostMapping("/internal/score/batch")
  GlobalRestResponse<ScoreBatchResponse> scoreBatch(
      @RequestHeader(INTERNAL_TOKEN_HEADER) String internalToken,
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid,
      @RequestBody ScoreBatchRequest request);
}
