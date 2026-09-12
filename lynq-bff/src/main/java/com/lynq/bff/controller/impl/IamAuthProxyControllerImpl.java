package com.lynq.bff.controller.impl;

import com.lynq.bff.client.LynqIamAuthClient;
import com.lynq.bff.service.IamAuthProxyService;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The auth routes the gateway relays to lynq-iam, mapped one exact path at a time and one exact
 * verb at a time. Nothing else under {@code /auth} is reachable: {@code /auth/validate} and
 * {@code /auth/user-info} have no browser caller — lynq-app-backend resolves the caller against
 * lynq-iam from inside the cluster — so they answer 404 here, and a route added to lynq-iam
 * tomorrow stays closed until it is named below.
 */
@RestController
public class IamAuthProxyControllerImpl implements com.lynq.bff.controller.IamAuthProxyController {

  private static final String AUTH_PREFIX = "/auth/";

  private final IamAuthProxyService iamAuthProxyService;
  private final LynqIamAuthClient lynqIamAuthClient;

  public IamAuthProxyControllerImpl(IamAuthProxyService iamAuthProxyService,
                                    LynqIamAuthClient lynqIamAuthClient) {
    this.iamAuthProxyService = iamAuthProxyService;
    this.lynqIamAuthClient = lynqIamAuthClient;
  }

  @Override
  @PostMapping({"/auth/register", "/auth/login/username", "/auth/login/email", "/auth/refresh"})
  public ResponseEntity<byte[]> relayAuthPost(HttpServletRequest request) throws IOException {
    return iamAuthProxyService.relay(lynqIamAuthClient, authPath(request), request);
  }

  @Override
  @GetMapping({"/auth/check-username", "/auth/check-email"})
  public ResponseEntity<byte[]> relayAuthCheck(HttpServletRequest request) throws IOException {
    return iamAuthProxyService.relay(lynqIamAuthClient, authPath(request), request);
  }

  @Override
  @PatchMapping("/auth/update-password")
  public ResponseEntity<byte[]> relayPasswordUpdate(HttpServletRequest request) throws IOException {
    return iamAuthProxyService.relay(lynqIamAuthClient, authPath(request), request);
  }

  static String authPath(HttpServletRequest request) {
    String path = request.getServletPath();
    return path.startsWith(AUTH_PREFIX) ? path.substring(AUTH_PREFIX.length()) : path;
  }
}
