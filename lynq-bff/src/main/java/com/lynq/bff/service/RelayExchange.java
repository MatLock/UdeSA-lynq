package com.lynq.bff.service;

import com.lynq.bff.exceptions.BadGatewayException;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

/**
 * The copying every relay does: the caller's request broken into the pieces a Feign call takes, and
 * the answer turned back into a response of our own. Method, path, query string, headers, body,
 * status code and response body all cross unchanged.
 *
 * <p>It lives apart from the services that relay because there are two of them — the DMZ one and
 * the lynq-iam auth one — and only what differs belongs to them: who to call, and whether the
 * verified caller id is injected on the way out.
 */
final class RelayExchange {

  static final String REQUEST_UUID_HEADER = "lynq-request-uuid";
  static final String USER_ID_HEADER = "user-id";
  static final String COMPANY_ID_HEADER = "company-id";

  private static final byte[] NO_BODY = new byte[0];

  private static final Set<String> SKIPPED_REQUEST_HEADERS = Set.of(
      "connection", "keep-alive", "proxy-authenticate", "proxy-authorization", "te", "trailer",
      "transfer-encoding", "upgrade", "host", "content-length", "expect",

      USER_ID_HEADER, COMPANY_ID_HEADER);

  private static final Set<String> SKIPPED_RESPONSE_HEADERS = Set.of(
      "connection", "keep-alive", "proxy-authenticate", "proxy-authorization", "te", "trailer",
      "transfer-encoding", "upgrade", "content-length", REQUEST_UUID_HEADER);

  private RelayExchange() {
  }

  static String method(HttpServletRequest request) {
    return request.getMethod().toUpperCase(Locale.ROOT);
  }

  static byte[] requestBody(HttpServletRequest request) throws IOException {
    try (InputStream in = request.getInputStream()) {
      return in.readAllBytes();
    }
  }

  static Map<String, Collection<String>> queryParameters(HttpServletRequest request) {
    Map<String, Collection<String>> query = new LinkedHashMap<>();
    request.getParameterMap().forEach((name, values) -> query.put(name, List.of(values)));
    return query;
  }

  /**
   * The caller's headers minus the hop-by-hop ones, and minus the identity headers a client must
   * not get to choose: {@code user-id} and {@code company-id} are what downstream services read as
   * the caller, so whatever the browser sent under those names is dropped here.
   */
  static Map<String, Collection<String>> requestHeaders(HttpServletRequest request) {
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

  static ResponseEntity<byte[]> toResponseEntity(Response response) {
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

  private static byte[] readResponseBody(Response response) {
    if (response.body() == null) {
      return NO_BODY;
    }
    try (InputStream in = response.body().asInputStream()) {
      return in.readAllBytes();
    } catch (IOException e) {
      throw new BadGatewayException("Could not read the relayed service's response body", e);
    }
  }
}
