package com.lynq.bff.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import org.springframework.http.ResponseEntity;

public interface DmzProxyController {

  @Operation(
      summary = "Proxy a request to a lynq-app-backend resource",
      description = "Relays `/user`, `/company` and `/job` to lynq-app-backend's DMZ API. The path, "
          + "the query string, the headers, the body, the status code and the response body all "
          + "cross unchanged.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Whatever lynq-app-backend answered; any "
          + "status it returns is passed through as-is."),
      @ApiResponse(responseCode = "401", description = "The Authorization header is missing, or the "
          + "access token's signature is invalid or expired."),
      @ApiResponse(responseCode = "403", description = "The lynq-request-uuid header is missing."),
      @ApiResponse(responseCode = "405", description = "The HTTP method is not one the gateway relays."),
      @ApiResponse(responseCode = "502", description = "lynq-app-backend could not be reached.")
  })
  ResponseEntity<byte[]> proxyToBackend(
      @Parameter(hidden = true) HttpServletRequest request) throws IOException;

  @Operation(
      summary = "Proxy a request to lynq-ml",
      description = "Relays `/skill-enhance`, `/translate`, `/detect-language` and "
          + "`/resume/skill-extraction` to lynq-ml's DMZ API. Each turns text the caller already "
          + "has into a result, so there is nothing for "
          + "lynq-app-backend to add. lynq-ml's other endpoints are not relayed — see "
          + "`refuseUnrelayedMlEndpoint`.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Whatever lynq-ml answered; any status it "
          + "returns is passed through as-is."),
      @ApiResponse(responseCode = "401", description = "The Authorization header is missing, or the "
          + "access token's signature is invalid or expired."),
      @ApiResponse(responseCode = "403", description = "The lynq-request-uuid header is missing."),
      @ApiResponse(responseCode = "405", description = "The HTTP method is not one the gateway relays."),
      @ApiResponse(responseCode = "502", description = "lynq-ml could not be reached.")
  })
  ResponseEntity<byte[]> proxyToMl(
      @Parameter(hidden = true) HttpServletRequest request) throws IOException;

  @Operation(
      summary = "Proxy a request to lynq-file-storage",
      description = "Relays `/files` to lynq-file-storage's DMZ API: file registration and the "
          + "pre-signed upload and download URLs. The bucket credentials never leave that service, "
          + "and the pre-signed URLs it hands back point straight at S3, not back through here. "
          + "Confirming or deleting a file requires being the user who registered it.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Whatever lynq-file-storage answered; any "
          + "status it returns is passed through as-is."),
      @ApiResponse(responseCode = "401", description = "The Authorization header is missing, or the "
          + "access token's signature is invalid or expired."),
      @ApiResponse(responseCode = "403", description = "The lynq-request-uuid header is missing, or "
          + "the file belongs to another user."),
      @ApiResponse(responseCode = "405", description = "The HTTP method is not one the gateway relays."),
      @ApiResponse(responseCode = "502", description = "lynq-file-storage could not be reached.")
  })
  ResponseEntity<byte[]> proxyToFileStorage(
      @Parameter(hidden = true) HttpServletRequest request) throws IOException;

  @Operation(
      summary = "Refuse a lynq-ml endpoint the gateway does not relay",
      description = "`upskilling_suggestion` and `candidate-explanation` are built from "
          + "lynq-app-backend's data and are reached through its job endpoints. `parse-resume` and "
          + "`resume-template-creation` fetch a caller-supplied URL server-side, which would make "
          + "the gateway an SSRF vector — `resume-template-creation` is driven by "
          + "`POST /resume/preview` instead, which signs those URLs itself. These are mapped only "
          + "so the refusal can say why; any other lynq-ml endpoint is a plain 404.")
  @ApiResponses({
      @ApiResponse(responseCode = "403", description = "Always. The reason names the route to use.")
  })
  ResponseEntity<byte[]> refuseUnrelayedMlEndpoint(
      @Parameter(hidden = true) HttpServletRequest request);
}
