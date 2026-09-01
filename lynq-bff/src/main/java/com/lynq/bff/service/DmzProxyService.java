package com.lynq.bff.service;

import com.lynq.bff.client.DmzClient;
import com.lynq.bff.exceptions.BadGatewayException;
import com.lynq.bff.exceptions.MethodNotAllowedException;
import com.lynq.bff.filter.JwtSignatureFilter;
import feign.FeignException;
import feign.Response;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

@Service
@Log4j2
public class DmzProxyService {

  private static final String REQUEST_UUID_HEADER = "lynq-request-uuid";
  private static final String USER_ID_HEADER = "user-id";
  private static final String COMPANY_ID_HEADER = "company-id";
  private static final byte[] NO_BODY = new byte[0];

  private static final Set<String> SKIPPED_REQUEST_HEADERS = Set.of(
      "connection", "keep-alive", "proxy-authenticate", "proxy-authorization", "te", "trailer",
      "transfer-encoding", "upgrade", "host", "content-length", "expect",

      USER_ID_HEADER, COMPANY_ID_HEADER);

  private static final Set<String> SKIPPED_RESPONSE_HEADERS = Set.of(
      "connection", "keep-alive", "proxy-authenticate", "proxy-authorization", "te", "trailer",
      "transfer-encoding", "upgrade", "content-length", REQUEST_UUID_HEADER);

  public ResponseEntity<byte[]> forward(DmzClient client, String downstreamPath,
                                        HttpServletRequest request) throws IOException {
    byte[] body = readRequestBody(request);
    Map<String, Collection<String>> query = queryParameters(request);
    Map<String, Collection<String>> headers = requestHeaders(request);
    headers.put(USER_ID_HEADER, List.of(verifiedUserId(request)));
    String method = request.getMethod().toUpperCase(Locale.ROOT);

    log.info("message= Proxying to DMZ, method={}, path={}", method, downstreamPath);

    Response downstreamResponse;
    try {
      downstreamResponse = call(client, method, downstreamPath, query, headers, body);
    } catch (FeignException e) {
      throw new BadGatewayException(
          "DMZ service could not be reached for " + method + " " + downstreamPath, e);
    }

    try (Response response = downstreamResponse) {
      log.info("message= DMZ answered, method={}, path={}, status={}",
          method, downstreamPath, response.status());
      return toResponseEntity(response);
    }
  }

  private Response call(DmzClient client, String method, String path,
                        Map<String, Collection<String>> query,
                        Map<String, Collection<String>> headers, byte[] body) {
    return switch (method) {
      case "GET" -> client.get(path, query, headers);
      case "POST" -> client.post(path, query, headers, body);
      case "PUT" -> client.put(path, query, headers, body);
      case "PATCH" -> client.patch(path, query, headers, body);
      case "DELETE" -> client.delete(path, query, headers);
      default -> throw new MethodNotAllowedException(
          "Method " + method + " is not proxied to the DMZ");
    };
  }

  private byte[] readRequestBody(HttpServletRequest request) throws IOException {
    try (InputStream in = request.getInputStream()) {
      return in.readAllBytes();
    }
  }

  private Map<String, Collection<String>> queryParameters(HttpServletRequest request) {
    Map<String, Collection<String>> query = new LinkedHashMap<>();
    request.getParameterMap().forEach((name, values) -> query.put(name, List.of(values)));
    return query;
  }

  private String verifiedUserId(HttpServletRequest request) {
    Object userId = request.getAttribute(JwtSignatureFilter.VERIFIED_USER_ID);
    if (userId == null) {
      throw new IllegalStateException(
          "No verified user id on the request: JwtSignatureFilter did not run");
    }
    return userId.toString();
  }

  private Map<String, Collection<String>> requestHeaders(HttpServletRequest request) {
    Map<String, Collection<String>> headers = new LinkedHashMap<>();
    Enumeration<String> names = request.getHeaderNames();
    while (names.hasMoreElements()) {
      String name = names.nextElement();
      if (SKIPPED_REQUEST_HEADERS.contains(name.toLowerCase(Locale.ROOT))) {
        continue;
      }
      headers.put(name, Collections.list(request.getHeaders(name)));
    }
    return headers;
  }

  private ResponseEntity<byte[]> toResponseEntity(Response response) {
    byte[] body = readResponseBody(response);
    HttpHeaders headers = new HttpHeaders();
    response.headers().forEach((name, values) -> {
      if (!SKIPPED_RESPONSE_HEADERS.contains(name.toLowerCase(Locale.ROOT))) {
        values.forEach(value -> headers.add(name, value));
      }
    });

    ResponseEntity.BodyBuilder builder = ResponseEntity.status(response.status()).headers(headers);

    return body.length == 0 ? builder.build() : builder.body(body);
  }

  private byte[] readResponseBody(Response response) {
    if (response.body() == null) {
      return NO_BODY;
    }
    try (InputStream in = response.body().asInputStream()) {
      return in.readAllBytes();
    } catch (IOException e) {
      throw new BadGatewayException("Could not read the DMZ service's response body", e);
    }
  }
}
