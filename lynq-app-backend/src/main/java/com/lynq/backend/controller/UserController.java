package com.lynq.backend.controller;

import com.lynq.backend.controller.request.CreateUserRequest;
import com.lynq.backend.controller.request.UpdateUserProfileRequest;
import com.lynq.backend.controller.response.CreateUserRestResponse;
import com.lynq.backend.controller.response.GlobalRestResponse;
import com.lynq.backend.controller.response.UpdateUserProfileRestResponse;
import com.lynq.backend.security.LynqUserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;

@Tag(name = "User", description = "Operations for managing Lynq platform users")
public interface UserController {

  @Operation(
      summary = "Create a new user",
      description = "Creates the profile of the authenticated user. The user identity is resolved "
          + "from the bearer token, so only the profile fields are supplied in the request body.",
      security = @SecurityRequirement(name = "bearerAuth"))
  @ApiResponses({
      @ApiResponse(
          responseCode = "201",
          description = "User created successfully",
          content = @Content(
              schema = @Schema(implementation = CreateUserRestResponse.class),
              examples = @ExampleObject(
                  name = "Created",
                  value = """
                      {
                        "success": true,
                        "data": {
                          "id": "550e8400-e29b-41d4-a716-446655440000",
                          "userType": "CANDIDATE",
                          "userProfileImageUrl": "https://cdn.lynq.com/avatars/jane.png",
                          "currentPosition": "Backend Engineer",
                          "about": "Java developer focused on distributed systems.",
                          "githubUrl": "https://github.com/janedoe",
                          "linkedinUrl": "https://linkedin.com/in/janedoe",
                          "birthDate": "1995-04-12",
                          "createdOn": "2026-06-25"
                        }
                      }"""))),
      @ApiResponse(
          responseCode = "400",
          description = "Validation failed on one or more request fields",
          content = @Content(
              examples = @ExampleObject(
                  name = "Invalid fields",
                  value = """
                      {
                        "success": false,
                        "data": {
                          "currentPosition": "must not be blank",
                          "birthDate": "must not be null"
                        },
                        "reason": "Invalid Fields Found"
                      }"""))),
      @ApiResponse(
          responseCode = "403",
          description = "Missing required lynq-request-uuid header",
          content = @Content(
              examples = @ExampleObject(
                  name = "Missing header",
                  value = """
                      {
                        "success": false,
                        "data": null,
                        "reason": "Missing required header"
                      }"""))),
      @ApiResponse(
          responseCode = "401",
          description = "Missing or invalid bearer token",
          content = @Content(
              examples = @ExampleObject(
                  name = "Unauthorized",
                  value = """
                      {
                        "success": false,
                        "data": null,
                        "reason": "Invalid or expired token"
                      }"""))),
      @ApiResponse(
          responseCode = "500",
          description = "Unexpected server error",
          content = @Content(
              examples = @ExampleObject(
                  name = "Server error",
                  value = """
                      {
                        "success": false,
                        "data": null,
                        "reason": "Unexpected error"
                      }""")))
  })
  @Parameters({
      @Parameter(
          name = "lynq-request-uuid",
          in = ParameterIn.HEADER,
          required = true,
          description = "Unique identifier for the request, echoed back in the response and used "
              + "for log correlation. Requests without it are rejected with 403.",
          example = "550e8400-e29b-41d4-a716-446655440000")
  })
  ResponseEntity<GlobalRestResponse<CreateUserRestResponse>> createUser(
      @io.swagger.v3.oas.annotations.parameters.RequestBody(
          description = "Profile details for the new user",
          required = true,
          content = @Content(examples = @ExampleObject(
              name = "Candidate profile",
              value = """
                  {
                    "userType": "CANDIDATE",
                    "userProfileImageUrl": "https://cdn.lynq.com/avatars/jane.png",
                    "currentPosition": "Backend Engineer",
                    "about": "Java developer focused on distributed systems.",
                    "githubUrl": "https://github.com/janedoe",
                    "linkedinUrl": "https://linkedin.com/in/janedoe",
                    "birthDate": "1995-04-12"
                  }""")))
      @Valid CreateUserRequest request,
      @Parameter(hidden = true) LynqUserPrincipal principal);

  @Operation(
      summary = "Update the authenticated user's profile",
      description = "Partially updates the profile of the authenticated user. The user identity is "
          + "resolved from the bearer token. Only the fields present in the request body are "
          + "modified; omitted fields keep their current value.",
      security = @SecurityRequirement(name = "bearerAuth"))
  @ApiResponses({
      @ApiResponse(
          responseCode = "200",
          description = "Profile updated successfully",
          content = @Content(
              schema = @Schema(implementation = UpdateUserProfileRestResponse.class),
              examples = @ExampleObject(
                  name = "Updated",
                  value = """
                      {
                        "success": true,
                        "data": {
                          "id": "550e8400-e29b-41d4-a716-446655440000",
                          "userType": "CANDIDATE",
                          "fullName": "Jane Doe",
                          "userProfileImageUrl": "https://cdn.lynq.com/avatars/jane.png",
                          "currentPosition": "Staff Engineer",
                          "about": "Java developer focused on distributed systems.",
                          "githubUrl": "https://github.com/janedoe",
                          "linkedinUrl": "https://linkedin.com/in/janedoe",
                          "birthDate": "1995-04-12",
                          "createdOn": "2026-06-25"
                        }
                      }"""))),
      @ApiResponse(
          responseCode = "404",
          description = "No user exists for the authenticated identity",
          content = @Content(
              examples = @ExampleObject(
                  name = "User not found",
                  value = """
                      {
                        "success": false,
                        "data": null,
                        "reason": "User '550e8400-e29b-41d4-a716-446655440000' not found"
                      }"""))),
      @ApiResponse(
          responseCode = "403",
          description = "Missing required lynq-request-uuid header",
          content = @Content(
              examples = @ExampleObject(
                  name = "Missing header",
                  value = """
                      {
                        "success": false,
                        "data": null,
                        "reason": "Missing required header"
                      }"""))),
      @ApiResponse(
          responseCode = "401",
          description = "Missing or invalid bearer token",
          content = @Content(
              examples = @ExampleObject(
                  name = "Unauthorized",
                  value = """
                      {
                        "success": false,
                        "data": null,
                        "reason": "Invalid or expired token"
                      }"""))),
      @ApiResponse(
          responseCode = "500",
          description = "Unexpected server error",
          content = @Content(
              examples = @ExampleObject(
                  name = "Server error",
                  value = """
                      {
                        "success": false,
                        "data": null,
                        "reason": "Unexpected error"
                      }""")))
  })
  @Parameters({
      @Parameter(
          name = "lynq-request-uuid",
          in = ParameterIn.HEADER,
          required = true,
          description = "Unique identifier for the request, echoed back in the response and used "
              + "for log correlation. Requests without it are rejected with 403.",
          example = "550e8400-e29b-41d4-a716-446655440000")
  })
  ResponseEntity<GlobalRestResponse<UpdateUserProfileRestResponse>> updateUserProfile(
      @io.swagger.v3.oas.annotations.parameters.RequestBody(
          description = "Profile fields to update. Only the supplied fields are modified.",
          required = true,
          content = @Content(examples = @ExampleObject(
              name = "Partial profile update",
              value = """
                  {
                    "fullName": "Jane Doe",
                    "currentPosition": "Staff Engineer"
                  }""")))
      @Valid UpdateUserProfileRequest request,
      @Parameter(hidden = true) LynqUserPrincipal principal);

}