package com.lynq.bff.service;

import com.lynq.bff.client.LynqIamAuthClient;
import com.lynq.bff.exceptions.BadGatewayException;
import com.lynq.bff.exceptions.MethodNotAllowedException;
import feign.FeignException;
import feign.Response;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

/**
 * Relays the auth endpoints the browser needs to lynq-iam, so the gateway is the only origin the
 * frontend talks to and lynq-iam needs no public route of its own.
 *
 * <p>It deliberately injects nothing. The DMZ relay replaces the {@code user-id} header with the
 * subject of the token it just verified, because the services behind it read that header as the
 * caller's identity. lynq-iam does not: it reads the credential it was handed — a password, a
 * refresh token, an access token — and checks it itself. On these routes there is often no verified
 * caller to speak of yet, which is the whole point of them.
 */
@Service
@Log4j2
public class IamAuthProxyService {

  public ResponseEntity<byte[]> relay(LynqIamAuthClient client, String authPath,
                                      HttpServletRequest request) throws IOException {
    byte[] body = RelayExchange.requestBody(request);
    Map<String, Collection<String>> query = RelayExchange.queryParameters(request);
    Map<String, Collection<String>> headers = RelayExchange.requestHeaders(request);
    String method = RelayExchange.method(request);

    log.info("message= Relaying to lynq-iam, method={}, path=auth/{}", method, authPath);

    Response iamResponse;
    try {
      iamResponse = call(client, method, authPath, query, headers, body);
    } catch (FeignException e) {
      throw new BadGatewayException(
          "lynq-iam could not be reached for " + method + " auth/" + authPath, e);
    }

    try (Response response = iamResponse) {
      log.info("message= lynq-iam answered, method={}, path=auth/{}, status={}",
          method, authPath, response.status());
      return RelayExchange.toResponseEntity(response);
    }
  }

  private Response call(LynqIamAuthClient client, String method, String path,
                        Map<String, Collection<String>> query,
                        Map<String, Collection<String>> headers, byte[] body) {
    return switch (method) {
      case "GET" -> client.get(path, query, headers);
      case "POST" -> client.post(path, query, headers, body);
      case "PATCH" -> client.patch(path, query, headers, body);
      default -> throw new MethodNotAllowedException(
          "Method " + method + " is not relayed to lynq-iam");
    };
  }
}
