package com.lynq.bff.controller.impl;

import com.lynq.bff.client.LynqBackendDmzClient;
import com.lynq.bff.client.LynqFileStorageDmzClient;
import com.lynq.bff.client.LynqMlDmzClient;
import com.lynq.bff.exceptions.ForbiddenException;
import com.lynq.bff.service.DmzProxyService;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Set;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DmzProxyControllerImpl implements com.lynq.bff.controller.DmzProxyController {

  private static final Set<String> ML_EVALUATIONS_OWNED_BY_BACKEND =
      Set.of("upskilling_suggestion", "candidate-explanation");

  private static final String USE_BACKEND_JOB_ENDPOINTS_INSTEAD =
      "This lynq-ml evaluation is built from lynq-backend's data and must be reached through its "
          + "job endpoints, not relayed from the browser";

  private static final String URL_TAKING_ENDPOINT_NOT_RELAYED =
      "This lynq-ml endpoint fetches a caller-supplied URL server-side and is not reachable "
          + "through the gateway; the resume preview is driven by POST /resume/preview, which "
          + "signs those URLs itself";

  private final DmzProxyService dmzProxyService;
  private final LynqBackendDmzClient lynqBackendDmzClient;
  private final LynqMlDmzClient lynqMlDmzClient;
  private final LynqFileStorageDmzClient lynqFileStorageDmzClient;

  public DmzProxyControllerImpl(DmzProxyService dmzProxyService,
                                LynqBackendDmzClient lynqBackendDmzClient,
                                LynqMlDmzClient lynqMlDmzClient,
                                LynqFileStorageDmzClient lynqFileStorageDmzClient) {
    this.dmzProxyService = dmzProxyService;
    this.lynqBackendDmzClient = lynqBackendDmzClient;
    this.lynqMlDmzClient = lynqMlDmzClient;
    this.lynqFileStorageDmzClient = lynqFileStorageDmzClient;
  }

  @Override
  @RequestMapping({"/user/**", "/company/**", "/job/**"})
  public ResponseEntity<byte[]> proxyToBackend(HttpServletRequest request) throws IOException {
    return dmzProxyService.forward(lynqBackendDmzClient, downstreamPath(request), request);
  }

  @Override
  @RequestMapping({"/skill-enhance", "/translate", "/detect-language",
      "/resume/skill-extraction"})
  public ResponseEntity<byte[]> proxyToMl(HttpServletRequest request) throws IOException {
    return dmzProxyService.forward(lynqMlDmzClient, downstreamPath(request), request);
  }

  @Override
  @RequestMapping("/files/**")
  public ResponseEntity<byte[]> proxyToFileStorage(HttpServletRequest request) throws IOException {
    return dmzProxyService.forward(lynqFileStorageDmzClient, downstreamPath(request), request);
  }

  @Override
  @RequestMapping({"/upskilling_suggestion", "/candidate-explanation",
      "/parse-resume", "/resume-template-creation"})
  public ResponseEntity<byte[]> refuseUnrelayedMlEndpoint(HttpServletRequest request) {
    throw new ForbiddenException(
        ML_EVALUATIONS_OWNED_BY_BACKEND.contains(downstreamPath(request))
            ? USE_BACKEND_JOB_ENDPOINTS_INSTEAD
            : URL_TAKING_ENDPOINT_NOT_RELAYED);
  }

  static String downstreamPath(HttpServletRequest request) {
    String path = request.getServletPath();
    return path.startsWith("/") ? path.substring(1) : path;
  }
}
