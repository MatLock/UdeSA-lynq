package com.lynq.bff.service;

import com.lynq.bff.client.DmzClient;
import com.lynq.bff.exceptions.BadGatewayException;
import com.lynq.bff.exceptions.MethodNotAllowedException;
import com.lynq.bff.filter.JwtSignatureFilter;
import feign.FeignException;
import feign.Response;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

@Service
@Log4j2
public class DmzProxyService {

  public ResponseEntity<byte[]> forward(DmzClient client, String downstreamPath,
                                        HttpServletRequest request) throws IOException {
    byte[] body = RelayExchange.requestBody(request);
    Map<String, Collection<String>> query = RelayExchange.queryParameters(request);
    Map<String, Collection<String>> headers = RelayExchange.requestHeaders(request);
    headers.put(RelayExchange.USER_ID_HEADER, List.of(verifiedUserId(request)));
    String method = RelayExchange.method(request);

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
      return RelayExchange.toResponseEntity(response);
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

  private String verifiedUserId(HttpServletRequest request) {
    Object userId = request.getAttribute(JwtSignatureFilter.VERIFIED_USER_ID);
    if (userId == null) {
      throw new IllegalStateException(
          "No verified user id on the request: JwtSignatureFilter did not run");
    }
    return userId.toString();
  }
}
