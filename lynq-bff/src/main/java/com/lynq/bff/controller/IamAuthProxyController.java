package com.lynq.bff.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import org.springframework.http.ResponseEntity;

public interface IamAuthProxyController {

  @Operation(
      summary = "Relay an anonymous or credential-bearing auth call to lynq-iam",
      description = "Relays `POST /auth/register`, `POST /auth/login/username`, "
          + "`POST /auth/login/email` and `POST /auth/refresh` to lynq-iam. These are the routes "
          + "that mint a session, so there is no access token to verify on the way in: the first "
          + "three carry a password in the body and the refresh carries the opaque refresh token "
          + "as its bearer credential, which is not a JWT and only lynq-iam can check. The gateway "
          + "relays them so the browser has a single origin, and lynq-iam keeps validating every "
          + "credential itself.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Whatever lynq-iam answered; any status it "
          + "returns is passed through as-is."),
      @ApiResponse(responseCode = "401", description = "On `/auth/refresh`, the Authorization "
          + "header is missing."),
      @ApiResponse(responseCode = "403", description = "The lynq-request-uuid header is missing."),
      @ApiResponse(responseCode = "502", description = "lynq-iam could not be reached.")
  })
  ResponseEntity<byte[]> relayAuthPost(
      @Parameter(hidden = true) HttpServletRequest request) throws IOException;

  @Operation(
      summary = "Relay a pre-registration availability check to lynq-iam",
      description = "Relays `GET /auth/check-username` and `GET /auth/check-email` to lynq-iam. "
          + "Both are public by definition — the registration form calls them before there is an "
          + "account, let alone a token — and take the value to test as a query parameter.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Whatever lynq-iam answered. A format or "
          + "availability failure is reported in the payload, not as an HTTP error."),
      @ApiResponse(responseCode = "403", description = "The lynq-request-uuid header is missing."),
      @ApiResponse(responseCode = "502", description = "lynq-iam could not be reached.")
  })
  ResponseEntity<byte[]> relayAuthCheck(
      @Parameter(hidden = true) HttpServletRequest request) throws IOException;

  @Operation(
      summary = "Relay the password update to lynq-iam",
      description = "Relays `PATCH /auth/update-password` to lynq-iam. Unlike the routes above "
          + "this one carries an access token, so the gateway verifies its signature first and "
          + "only then relays — lynq-iam validates it again before rotating the password.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Whatever lynq-iam answered; on success the "
          + "user with a freshly minted access and refresh token."),
      @ApiResponse(responseCode = "401", description = "The Authorization header is missing, or the "
          + "access token's signature is invalid or expired."),
      @ApiResponse(responseCode = "403", description = "The lynq-request-uuid header is missing."),
      @ApiResponse(responseCode = "502", description = "lynq-iam could not be reached.")
  })
  ResponseEntity<byte[]> relayPasswordUpdate(
      @Parameter(hidden = true) HttpServletRequest request) throws IOException;
}
