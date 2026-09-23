package com.lynq.bff.client;

import com.lynq.bff.client.request.MarkConversationAppliedRequest;
import com.lynq.bff.client.request.StartTailorConversationRequest;
import com.lynq.bff.client.request.TailorTurnRequest;
import com.lynq.bff.controller.response.GlobalRestResponse;
import java.util.Map;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "lynqAgent", url = "${lynq.agent.url}")
public interface LynqAgentClient {

  String REQUEST_UUID_HEADER = "lynq-request-uuid";
  String USER_ID_HEADER = "user-id";

  @PostMapping("/dmz/conversation")
  GlobalRestResponse<Object> startConversation(
      @RequestBody StartTailorConversationRequest request,
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid,
      @RequestHeader(USER_ID_HEADER) String userId);

  @PostMapping("/dmz/conversation/{conversationId}/turn")
  GlobalRestResponse<Object> takeTurn(
      @PathVariable("conversationId") String conversationId,
      @RequestBody TailorTurnRequest request,
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid,
      @RequestHeader(USER_ID_HEADER) String userId);

  @GetMapping("/dmz/conversation/{conversationId}")
  GlobalRestResponse<Map<String, Object>> getConversation(
      @PathVariable("conversationId") String conversationId,
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid,
      @RequestHeader(USER_ID_HEADER) String userId);

  @PatchMapping("/dmz/conversation/{conversationId}/applied")
  GlobalRestResponse<Map<String, Object>> markApplied(
      @PathVariable("conversationId") String conversationId,
      @RequestBody MarkConversationAppliedRequest request,
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid,
      @RequestHeader(USER_ID_HEADER) String userId);
}
