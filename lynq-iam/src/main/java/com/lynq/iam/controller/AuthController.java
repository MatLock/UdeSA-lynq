package com.lynq.iam.controller;

import com.lynq.iam.controller.request.CreateUserRequest;
import com.lynq.iam.controller.request.EmailUserLogin;
import com.lynq.iam.controller.request.UserUpdatePasswordRequest;
import com.lynq.iam.controller.request.UsernameLogin;
import com.lynq.iam.controller.response.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;

@Tag(name = "Users", description = "User management operations")
public interface AuthController {

  @Operation(summary = "Create a new user", description = "Registers a new user with unique username and email")
  @Parameters({
    @Parameter(name = "lynq-request-uuid", in = ParameterIn.HEADER, required = true,
      description = "Per-request correlation UUID used for log tracing",
      example = "550e8400-e29b-41d4-a716-446655440000")
  })
  @ApiResponses({
    @ApiResponse(responseCode = "201", description = "User created successfully",
      content = @Content(schema = @Schema(implementation = UserRestResponse.class))),
    @ApiResponse(responseCode = "400", description = "Invalid request fields",
      content = @Content(schema = @Schema(implementation = ErrorRestResponse.class))),
    @ApiResponse(responseCode = "409", description = "Username or email already exists",
      content = @Content(schema = @Schema(implementation = ErrorRestResponse.class)))
  })
  ResponseEntity<GlobalRestResponse<UserRestResponse>> createUser(@Valid CreateUserRequest request);


  @Operation(summary = "Update password", description = "Updates the user's password and returns new access and refresh tokens")
  @SecurityRequirement(name = "bearerAuth")
  @Parameters({
    @Parameter(name = "lynq-request-uuid", in = ParameterIn.HEADER, required = true,
      description = "Per-request correlation UUID used for log tracing",
      example = "550e8400-e29b-41d4-a716-446655440000")
  })
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Password updated successfully",
      content = @Content(schema = @Schema(implementation = UserRestResponse.class))),
    @ApiResponse(responseCode = "400", description = "Invalid request fields",
      content = @Content(schema = @Schema(implementation = ErrorRestResponse.class))),
    @ApiResponse(responseCode = "401", description = "Missing or invalid access token",
      content = @Content(schema = @Schema(implementation = ErrorRestResponse.class))),
    @ApiResponse(responseCode = "403", description = "User not found",
      content = @Content(schema = @Schema(implementation = ErrorRestResponse.class)))
  })
  ResponseEntity<GlobalRestResponse<UserRestResponse>> updatePassword(
      @Parameter(hidden = true) @NotBlank String accessToken,
      @Valid UserUpdatePasswordRequest userUpdatePasswordRequest);

  @Operation(summary = "Login by username", description = "Authenticates a user by username and password, returns access and refresh tokens")
  @Parameters({
    @Parameter(name = "lynq-request-uuid", in = ParameterIn.HEADER, required = true,
      description = "Per-request correlation UUID used for log tracing",
      example = "550e8400-e29b-41d4-a716-446655440000")
  })
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Login successful",
      content = @Content(schema = @Schema(implementation = UserRestResponse.class))),
    @ApiResponse(responseCode = "400", description = "Invalid request fields",
      content = @Content(schema = @Schema(implementation = ErrorRestResponse.class))),
    @ApiResponse(responseCode = "403", description = "Invalid username or password",
      content = @Content(schema = @Schema(implementation = ErrorRestResponse.class)))
  })
  ResponseEntity<GlobalRestResponse<UserRestResponse>> loginByUsername(@Valid UsernameLogin usernameLoginRequest);

  @Operation(summary = "Login by email", description = "Authenticates a user by email and password, returns access and refresh tokens")
  @Parameters({
    @Parameter(name = "lynq-request-uuid", in = ParameterIn.HEADER, required = true,
      description = "Per-request correlation UUID used for log tracing",
      example = "550e8400-e29b-41d4-a716-446655440000")
  })
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Login successful",
      content = @Content(schema = @Schema(implementation = UserRestResponse.class))),
    @ApiResponse(responseCode = "400", description = "Invalid request fields",
      content = @Content(schema = @Schema(implementation = ErrorRestResponse.class))),
    @ApiResponse(responseCode = "403", description = "Invalid email or password",
      content = @Content(schema = @Schema(implementation = ErrorRestResponse.class)))
  })
  ResponseEntity<GlobalRestResponse<UserRestResponse>> loginByEmail(@Valid EmailUserLogin emailUserLoginRequest);

  @Operation(summary = "Validate access token", description = "Checks if the provided access token is valid and not expired")
  @SecurityRequirement(name = "bearerAuth")
  @Parameters({
    @Parameter(name = "lynq-request-uuid", in = ParameterIn.HEADER, required = true,
      description = "Per-request correlation UUID used for log tracing",
      example = "550e8400-e29b-41d4-a716-446655440000")
  })
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "Token validation result",
      content = @Content(schema = @Schema(example = "{\"success\": true, \"data\": true}"))),
    @ApiResponse(responseCode = "401", description = "Missing Authorization header",
      content = @Content(schema = @Schema(implementation = ErrorRestResponse.class)))
  })
  ResponseEntity<GlobalRestResponse<Boolean>> isAccessTokenValid(
      @Parameter(hidden = true) @NotBlank String accessToken);

  @Operation(summary = "Refresh access token", description = "Generates a new access token using a valid refresh token")
  @SecurityRequirement(name = "bearerAuth")
  @Parameters({
    @Parameter(name = "lynq-request-uuid", in = ParameterIn.HEADER, required = true,
      description = "Per-request correlation UUID used for log tracing",
      example = "550e8400-e29b-41d4-a716-446655440000")
  })
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "New access token generated",
      content = @Content(schema = @Schema(implementation = AccessTokenRefreshedResponse.class))),
    @ApiResponse(responseCode = "401", description = "Missing Authorization header",
      content = @Content(schema = @Schema(implementation = ErrorRestResponse.class))),
    @ApiResponse(responseCode = "403", description = "User not found",
      content = @Content(schema = @Schema(implementation = ErrorRestResponse.class))),
    @ApiResponse(responseCode = "403", description = "Invalid or expired refresh token",
      content = @Content(schema = @Schema(implementation = ErrorRestResponse.class)))
  })
  ResponseEntity<GlobalRestResponse<AccessTokenRefreshedResponse>> generateNewAccessToken(
      @Parameter(hidden = true) @NotBlank String refreshToken);

  @Operation(summary = "Get user info from access token", description = "Extracts user identity (id, username, email) from a valid access token")
  @SecurityRequirement(name = "bearerAuth")
  @Parameters({
    @Parameter(name = "lynq-request-uuid", in = ParameterIn.HEADER, required = true,
      description = "Per-request correlation UUID used for log tracing",
      example = "550e8400-e29b-41d4-a716-446655440000")
  })
  @ApiResponses({
    @ApiResponse(responseCode = "200", description = "User info extracted from token",
      content = @Content(schema = @Schema(implementation = UserInfoRestResponse.class))),
    @ApiResponse(responseCode = "401", description = "Missing or invalid access token",
      content = @Content(schema = @Schema(implementation = ErrorRestResponse.class)))
  })
  ResponseEntity<GlobalRestResponse<UserInfoRestResponse>> obtainUserInfoFromToken(@Parameter(hidden = true) @NotBlank String accessToken);


}