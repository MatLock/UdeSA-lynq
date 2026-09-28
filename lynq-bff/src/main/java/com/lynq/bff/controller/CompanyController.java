package com.lynq.bff.controller;

import com.lynq.bff.client.request.CreateUserWithCompanyRequest;
import com.lynq.bff.client.request.UpdateCompanyRequest;
import com.lynq.bff.client.response.CreateUserWithCompanyResponse;
import com.lynq.bff.client.response.GenerateUploadImageResponse;
import com.lynq.bff.client.response.GetCompanyDetailResponse;
import com.lynq.bff.client.response.UpdateCompanyResponse;
import com.lynq.bff.controller.response.GlobalRestResponse;
import com.lynq.bff.security.LynqUserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.http.ResponseEntity;

@ApiResponses({
    @ApiResponse(responseCode = "401", description = "The Authorization header is missing, or the "
        + "access token's signature is invalid or expired."),
    @ApiResponse(responseCode = "403", description = "The lynq-request-uuid header is missing, or "
        + "the caller does not hold the role the route requires."),
    @ApiResponse(responseCode = "502", description = "lynq-app-backend could not be reached.")
})
public interface CompanyController {

  @Operation(summary = "Create a company and make the authenticated user its owner")
  ResponseEntity<GlobalRestResponse<CreateUserWithCompanyResponse>> createUserWithCompany(
      CreateUserWithCompanyRequest request,
      @Parameter(hidden = true) String requestUuid,
      @Parameter(hidden = true) LynqUserPrincipal principal);

  @Operation(summary = "Update the company of the authenticated user")
  ResponseEntity<GlobalRestResponse<UpdateCompanyResponse>> updateCompany(
      UpdateCompanyRequest request,
      @Parameter(hidden = true) String requestUuid,
      @Parameter(hidden = true) LynqUserPrincipal principal);

  @Operation(summary = "Issue a pre-signed url to upload the company's profile image")
  ResponseEntity<GlobalRestResponse<GenerateUploadImageResponse>> generateImageUploadUrl(
      String fileName,
      @Parameter(hidden = true) String requestUuid,
      @Parameter(hidden = true) LynqUserPrincipal principal);

  @Operation(summary = "Confirm the company's profile image upload")
  ResponseEntity<Void> confirmImageUpload(
      String fileId,
      @Parameter(hidden = true) String requestUuid,
      @Parameter(hidden = true) LynqUserPrincipal principal);

  @Operation(summary = "Read a company and the job posts it published")
  @ApiResponses(@ApiResponse(responseCode = "404", description = "No company holds that id."))
  ResponseEntity<GlobalRestResponse<GetCompanyDetailResponse>> getCompanyDetail(
      String companyId,
      @Parameter(hidden = true) String requestUuid,
      @Parameter(hidden = true) LynqUserPrincipal principal);
}
