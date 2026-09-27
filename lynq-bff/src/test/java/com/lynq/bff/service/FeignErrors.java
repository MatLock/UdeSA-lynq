package com.lynq.bff.service;

import feign.FeignException;
import feign.Request;
import feign.RequestTemplate;
import feign.Response;
import java.nio.charset.StandardCharsets;
import java.util.Map;

final class FeignErrors {

  private FeignErrors() {
  }

  static FeignException status(int status, String body) {
    Response response = Response.builder()
        .status(status)
        .request(anyRequest())
        .headers(Map.of())
        .body(body, StandardCharsets.UTF_8)
        .build();

    return FeignException.errorStatus("SomeClient#someMethod()", response);
  }

  static FeignException status(int status) {
    return status(status, null);
  }

  static FeignException unreachable() {
    return new FeignException.FeignServerException(
        -1, "Connection refused", anyRequest(), null, Map.of()) {
    };
  }

  private static Request anyRequest() {
    return Request.create(Request.HttpMethod.GET, "/downstream", Map.of(), null,
        new RequestTemplate());
  }
}
