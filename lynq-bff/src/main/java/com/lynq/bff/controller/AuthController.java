package com.lynq.bff.controller;

import com.lynq.bff.client.request.EmailLoginRequest;
import com.lynq.bff.client.request.RegisterUserRequest;
import com.lynq.bff.client.request.UpdatePasswordRequest;
import com.lynq.bff.client.request.UsernameLoginRequest;
import com.lynq.bff.client.response.AccessTokenRefreshedResponse;
import com.lynq.bff.client.response.AuthUserResponse;
import com.lynq.bff.client.response.CheckEmailResponse;
import com.lynq.bff.client.response.CheckUsernameResponse;
import com.lynq.bff.controller.response.GlobalRestResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.http.ResponseEntity;

/**
 * The auth routes the gateway relays to lynq-iam, one exact path and one exact verb at a time.
 * Nothing else under {@code /auth} is reachable: {@code /auth/validate} and {@code /auth/user-info}
 * have no browser caller — lynq-app-backend resolves the caller against lynq-iam from inside the
 * cluster — so they answer 404 here, and a route added to lynq-iam tomorrow stays closed until it
 * is named below.
 */
@ApiResponses({
    @ApiResponse(responseCode = "403", description = "The lynq-request-uuid header is missing."),
    @ApiResponse(responseCode = "502", description = "lynq-iam could not be reached.")
})
public interface AuthController {

  @Operation(
      summary = "Register an account",
      description = "The refresh token is not in the body: it is set as the HttpOnly lynq_refresh "
          + "cookie, a session one here. The logins and the password update do the same.")
  @ApiResponses(@ApiResponse(responseCode = "409", description = "The username or email is taken."))
  ResponseEntity<GlobalRestResponse<AuthUserResponse>> register(
      RegisterUserRequest request,
      @Parameter(hidden = true) String requestUuid);

  @Operation(summary = "Log in with a username")
  @ApiResponses(@ApiResponse(responseCode = "401", description = "The credentials do not match."))
  ResponseEntity<GlobalRestResponse<AuthUserResponse>> loginByUsername(
      UsernameLoginRequest request,
      @Parameter(description = "Keep the session across browser restarts: the refresh cookie "
          + "persists instead of ending with the browser session.") boolean remember,
      @Parameter(hidden = true) String requestUuid);

  @Operation(summary = "Log in with an email address")
  @ApiResponses(@ApiResponse(responseCode = "401", description = "The credentials do not match."))
  ResponseEntity<GlobalRestResponse<AuthUserResponse>> loginByEmail(
      EmailLoginRequest request,
      @Parameter(description = "Keep the session across browser restarts: the refresh cookie "
          + "persists instead of ending with the browser session.") boolean remember,
      @Parameter(hidden = true) String requestUuid);

  @Operation(
      summary = "Mint a new access token",
      description = "The credential is the opaque refresh token, read from the HttpOnly "
          + "lynq_refresh cookie the login set: no script in the browser can read it. It is not a "
          + "JWT and the gateway holds no secret that could check it, so it crosses untouched and "
          + "lynq-iam decides.")
  @ApiResponses({
      @ApiResponse(responseCode = "401", description = "The lynq_refresh cookie is missing."),
      @ApiResponse(responseCode = "403", description = "The refresh token is unknown or expired.")
  })
  ResponseEntity<GlobalRestResponse<AccessTokenRefreshedResponse>> refresh(
      @Parameter(hidden = true) String refreshToken,
      @Parameter(hidden = true) String requestUuid);

  @Operation(
      summary = "Log out",
      description = "Expires the HttpOnly lynq_refresh cookie, which the browser cannot remove on "
          + "its own.")
  @ApiResponses(@ApiResponse(responseCode = "204", description = "The cookie is cleared."))
  ResponseEntity<Void> logout();

  @Operation(summary = "Change the authenticated user's password")
  @ApiResponses(@ApiResponse(responseCode = "401", description = "The access token is invalid or "
      + "expired."))
  ResponseEntity<GlobalRestResponse<AuthUserResponse>> updatePassword(
      UpdatePasswordRequest request,
      @Parameter(description = "Keep the session across browser restarts: the refresh cookie "
          + "persists instead of ending with the browser session.") boolean remember,
      @Parameter(hidden = true) String authorization,
      @Parameter(hidden = true) String requestUuid);

  @Operation(summary = "Check whether a username is valid and available")
  ResponseEntity<GlobalRestResponse<CheckUsernameResponse>> checkUsername(
      String username,
      @Parameter(hidden = true) String requestUuid);

  @Operation(summary = "Check whether an email address is valid and available")
  ResponseEntity<GlobalRestResponse<CheckEmailResponse>> checkEmail(
      String email,
      @Parameter(hidden = true) String requestUuid);
}
