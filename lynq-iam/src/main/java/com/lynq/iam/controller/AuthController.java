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
import org.apache.logging.log4j.core.config.plugins.validation.constraints.NotBlank;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;

@Tag(name = "Users", description = "User management operations")
@Validated
@RequestMapping("/auth")
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
  GlobalRestResponse<UserRestResponse> createUser(@Valid @RequestBody CreateUserRequest request);


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
  GlobalRestResponse<UserRestResponse> updatePassword(
      @Parameter(hidden = true)
      @RequestHeader("Authorization") @NotBlank String accessToken,
      @Valid @RequestBody UserUpdatePasswordRequest userUpdatePasswordRequest);

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
  GlobalRestResponse<UserRestResponse> loginByUsername(@Valid @RequestBody UsernameLogin usernameLoginRequest);

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
  GlobalRestResponse<UserRestResponse> loginByEmail(@Valid @RequestBody EmailUserLogin emailUserLoginRequest);

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
  GlobalRestResponse<Boolean> isAccessTokenValid(
      @Parameter(hidden = true)
      @RequestHeader("Authorization") @NotBlank String accessToken);

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
  GlobalRestResponse<AccessTokenRefreshedResponse> generateNewAccessToken(
      @Parameter(hidden = true) @RequestHeader("Authorization") @NotBlank String refreshToken);

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
  GlobalRestResponse<UserInfoRestResponse> obtainUserInfoFromToken(
      @Parameter(hidden = true) @RequestHeader("Authorization") @NotBlank String accessToken);
}
