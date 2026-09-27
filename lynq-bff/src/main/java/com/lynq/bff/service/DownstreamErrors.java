package com.lynq.bff.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynq.bff.exceptions.BadGatewayException;
import com.lynq.bff.exceptions.BadRequestException;
import com.lynq.bff.exceptions.ConflictException;
import com.lynq.bff.exceptions.ForbiddenException;
import com.lynq.bff.exceptions.MethodNotAllowedException;
import com.lynq.bff.exceptions.NotFoundException;
import com.lynq.bff.exceptions.UnauthorizedException;
import feign.FeignException;
import java.util.function.Supplier;

final class DownstreamErrors {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static final String REASON_FIELD = "reason";
  private static final String CODE_FIELD = "code";

  private DownstreamErrors() {
  }

  static <T> T call(Supplier<T> downstreamCall, String failureMessage) {
    try {
      return downstreamCall.get();
    } catch (FeignException e) {
      throw translate(e, failureMessage);
    } catch (RuntimeException e) {
      throw new BadGatewayException(failureMessage, e);
    }
  }

  static void run(Runnable downstreamCall, String failureMessage) {
    call(() -> {
      downstreamCall.run();
      return null;
    }, failureMessage);
  }

  private static RuntimeException translate(FeignException e, String failureMessage) {
    JsonNode body = body(e);
    String reason = text(body, REASON_FIELD, failureMessage);

    return switch (e.status()) {
      case 400 -> new BadRequestException(reason);
      case 401 -> new UnauthorizedException(reason);
      case 403 -> new ForbiddenException(reason);
      case 404 -> new NotFoundException(reason);
      case 405 -> new MethodNotAllowedException(reason);
      case 409 -> new ConflictException(reason, text(body, CODE_FIELD, null));
      default -> new BadGatewayException(failureMessage, e);
    };
  }

  private static JsonNode body(FeignException e) {
    String content = e.contentUTF8();
    if (content.isBlank()) {
      return null;
    }
    try {
      return MAPPER.readTree(content);
    } catch (JsonProcessingException notJson) {
      return null;
    }
  }

  private static String text(JsonNode body, String field, String fallback) {
    if (body == null) {
      return fallback;
    }
    JsonNode value = body.path(field);
    return value.isTextual() ? value.asText() : fallback;
  }
}
