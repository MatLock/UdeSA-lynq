package com.lynq.backend.controller;

import com.lynq.backend.controller.request.CreateUserWithCompanyRequest;
import com.lynq.backend.controller.request.UpdateCompanyRequest;
import com.lynq.backend.controller.response.CreateUserWithCompanyRestResponse;
import com.lynq.backend.controller.response.GenerateUploadImageRestResponse;
import com.lynq.backend.controller.response.GetCompanyDetailRestResponse;
import com.lynq.backend.controller.response.GlobalRestResponse;
import com.lynq.backend.controller.response.UpdateCompanyRestResponse;
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

@Tag(name = "Company", description = "Operations for managing Lynq platform companies")
public interface CompanyController {

  @Operation(
      summary = "Create a company together with its owner profile",
      description = "Creates the profile of the authenticated user as a COMPANY-type user and the "
          + "company they own in a single call. The owner identity is resolved from the bearer "
          + "token, while the company id is generated server-side.",
      security = @SecurityRequirement(name = "bearerAuth"))
  @ApiResponses({
      @ApiResponse(
          responseCode = "201",
          description = "User and company created successfully",
          content = @Content(
              schema = @Schema(implementation = CreateUserWithCompanyRestResponse.class),
              examples = @ExampleObject(
                  name = "Created",
                  value = """
                      {
                        "success": true,
                        "data": {
                          "companyId": "018f9c3a-2b1d-7c4e-9a6f-1e2d3c4b5a60",
                          "companyName": "Lynq",
                          "companyAbout": "Hiring platform for engineers.",
                          "companySize": 42,
                          "companyProfileImageUrl": "https://cdn.lynq.com/logos/lynq.png",
                          "companyCreatedOn": "2026-06-25",
                          "ownerUserId": "550e8400-e29b-41d4-a716-446655440000"
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
                          "companyName": "must not be blank",
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
  ResponseEntity<GlobalRestResponse<CreateUserWithCompanyRestResponse>> createUserWithCompany(
      @io.swagger.v3.oas.annotations.parameters.RequestBody(
          description = "Owner profile details and company details",
          required = true,
          content = @Content(examples = @ExampleObject(
              name = "Company owner",
              value = """
                  {
                    "userProfileImageUrl": "https://cdn.lynq.com/avatars/jane.png",
                    "currentPosition": "Founder",
                    "userAbout": "Building the Lynq hiring platform.",
                    "linkedinUrl": "https://linkedin.com/in/janedoe",
                    "birthDate": "1995-04-12",
                    "companyName": "Lynq",
                    "companyAbout": "Hiring platform for engineers.",
                    "companySize": 42,
                    "companyProfileImageUrl": "https://cdn.lynq.com/logos/lynq.png"
                  }""")))
      @Valid CreateUserWithCompanyRequest request,
      @Parameter(hidden = true) LynqUserPrincipal principal);

  @Operation(
      summary = "Generate a pre-signed URL to upload the authenticated owner's company logo",
      description = "Builds the S3 path for the given file name, persists it as the profile image "
          + "reference of the company owned by the authenticated user, and returns a short-lived "
          + "pre-signed URL. The frontend uploads the image binary directly to S3 with an HTTP PUT "
          + "against the returned URL. Calling this endpoint again replaces the stored reference, "
          + "so the company logo can be changed at any time.",
      security = @SecurityRequirement(name = "bearerAuth"))
  @ApiResponses({
      @ApiResponse(
          responseCode = "200",
          description = "Pre-signed upload URL generated successfully",
          content = @Content(
              schema = @Schema(implementation = GenerateUploadImageRestResponse.class),
              examples = @ExampleObject(
                  name = "Pre-signed URL",
                  value = """
                      {
                        "success": true,
                        "data": {
                          "preSignedUrl": "https://lynq-bucket.s3.amazonaws.com/lynq/companies/018f9c3a-2b1d-7c4e-9a6f-1e2d3c4b5a60/profile/logo.png?X-Amz-Signature=..."
                        }
                      }"""))),
      @ApiResponse(
          responseCode = "404",
          description = "No company is owned by the authenticated user",
          content = @Content(
              examples = @ExampleObject(
                  name = "Company not found",
                  value = """
                      {
                        "success": false,
                        "data": null,
                        "reason": "No company owned by user '550e8400-e29b-41d4-a716-446655440000'"
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
                      }""")))
  })
  @Parameters({
      @Parameter(
          name = "lynq-request-uuid",
          in = ParameterIn.HEADER,
          required = true,
          description = "Unique identifier for the request, echoed back in the response and used "
              + "for log correlation. Requests without it are rejected with 403.",
          example = "550e8400-e29b-41d4-a716-446655440000"),
      @Parameter(
          name = "file-name",
          in = ParameterIn.QUERY,
          required = true,
          description = "Name of the company logo file to upload. Used to build the S3 object key.",
          example = "logo.png")
  })
  ResponseEntity<GlobalRestResponse<GenerateUploadImageRestResponse>> generateCompanyImageUploadUrl(
      String fileName,
      @Parameter(hidden = true) LynqUserPrincipal principal);

  @Operation(
      summary = "Get a company's details",
      description = "Returns the full profile of the company identified by the given id (name, "
          + "about, size, profile image and creation date) together with every job position the "
          + "company has posted, regardless of the job's status (open or closed). Any authenticated "
          + "user may retrieve a company's details. Fails with 404 when no company matches the id.",
      security = @SecurityRequirement(name = "bearerAuth"))
  ResponseEntity<GlobalRestResponse<GetCompanyDetailRestResponse>> getCompanyDetail(
      String companyId);

  @Operation(
      summary = "Update the authenticated owner's company",
      description = "Partially updates the company owned by the authenticated user. Only the fields "
          + "present in the request body are modified (name, about, size); omit a field or send it "
          + "as null to keep its current value. The company logo is changed separately through the "
          + "pre-signed upload flow. The company is resolved from the bearer token, so a user can "
          + "only edit their own company. Renaming to a name already used by another company is "
          + "rejected with 400.",
      security = @SecurityRequirement(name = "bearerAuth"))
  @ApiResponses({
      @ApiResponse(
          responseCode = "200",
          description = "Company updated successfully",
          content = @Content(
              schema = @Schema(implementation = UpdateCompanyRestResponse.class),
              examples = @ExampleObject(
                  name = "Updated",
                  value = """
                      {
                        "success": true,
                        "data": {
                          "id": "018f9c3a-2b1d-7c4e-9a6f-1e2d3c4b5a60",
                          "name": "Lynq",
                          "about": "Hiring platform for engineers.",
                          "size": 48,
                          "profileImageUrl": "https://lynq-bucket.s3.amazonaws.com/lynq/companies/018f9c3a-2b1d-7c4e-9a6f-1e2d3c4b5a60/profile/logo.png?X-Amz-Signature=...",
                          "createdOn": "2026-06-25"
                        }
                      }"""))),
      @ApiResponse(
          responseCode = "400",
          description = "The requested name is already used by another company",
          content = @Content(
              examples = @ExampleObject(
                  name = "Name taken",
                  value = """
                      {
                        "success": false,
                        "data": null,
                        "reason": "A company with name 'Lynq' already exists"
                      }"""))),
      @ApiResponse(
          responseCode = "404",
          description = "No company is owned by the authenticated user",
          content = @Content(
              examples = @ExampleObject(
                  name = "Company not found",
                  value = """
                      {
                        "success": false,
                        "data": null,
                        "reason": "No company owned by user '550e8400-e29b-41d4-a716-446655440000'"
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
  ResponseEntity<GlobalRestResponse<UpdateCompanyRestResponse>> updateCompany(
      @io.swagger.v3.oas.annotations.parameters.RequestBody(
          description = "Company fields to update (partial)",
          required = true,
          content = @Content(examples = @ExampleObject(
              name = "Update company",
              value = """
                  {
                    "name": "Lynq",
                    "about": "Hiring platform for engineers.",
                    "size": 48
                  }""")))
      UpdateCompanyRequest request,
      @Parameter(hidden = true) LynqUserPrincipal principal);

}