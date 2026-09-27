package com.lynq.bff.controller;

import com.lynq.bff.client.request.CreateResumeRequest;
import com.lynq.bff.client.request.CreateUserRequest;
import com.lynq.bff.client.request.UpdateResumeAliasRequest;
import com.lynq.bff.client.request.UpdateUserProfileRequest;
import com.lynq.bff.client.response.CreateUserResponse;
import com.lynq.bff.client.response.DeletedResumeResponse;
import com.lynq.bff.client.response.GenerateUploadImageResponse;
import com.lynq.bff.client.response.GenerateUploadResumeResponse;
import com.lynq.bff.client.response.GetUserProfileResponse;
import com.lynq.bff.client.response.GetUserResponse;
import com.lynq.bff.client.response.PagedResponse;
import com.lynq.bff.client.response.SupportedLanguageResponse;
import com.lynq.bff.client.response.UpdateUserProfileResponse;
import com.lynq.bff.client.response.UpskillingSuggestionResponse;
import com.lynq.bff.client.response.UserApplicationResponse;
import com.lynq.bff.client.response.UserResumeResponse;
import com.lynq.bff.controller.response.GlobalRestResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import java.util.List;
import org.springframework.http.ResponseEntity;

@ApiResponses({
    @ApiResponse(responseCode = "401", description = "The Authorization header is missing, or the "
        + "access token's signature is invalid or expired."),
    @ApiResponse(responseCode = "403", description = "The lynq-request-uuid header is missing, or "
        + "the caller does not hold the role the route requires."),
    @ApiResponse(responseCode = "502", description = "lynq-app-backend could not be reached.")
})
public interface UserController {

  @Operation(summary = "Read the authenticated user")
  ResponseEntity<GlobalRestResponse<GetUserResponse>> getUser(
      @Parameter(hidden = true) String requestUuid,
      @Parameter(hidden = true) String authorization,
      @Parameter(hidden = true) String userId);

  @Operation(summary = "Create the profile of the authenticated user")
  ResponseEntity<GlobalRestResponse<CreateUserResponse>> createUser(
      CreateUserRequest request,
      @Parameter(hidden = true) String requestUuid,
      @Parameter(hidden = true) String authorization,
      @Parameter(hidden = true) String userId);

  @Operation(summary = "Update the profile of the authenticated user")
  ResponseEntity<GlobalRestResponse<UpdateUserProfileResponse>> updateUserProfile(
      UpdateUserProfileRequest request,
      @Parameter(hidden = true) String requestUuid,
      @Parameter(hidden = true) String authorization,
      @Parameter(hidden = true) String userId);

  @Operation(summary = "Issue a pre-signed url to upload the user's profile image")
  ResponseEntity<GlobalRestResponse<GenerateUploadImageResponse>> generateImageUploadUrl(
      String fileName,
      @Parameter(hidden = true) String requestUuid,
      @Parameter(hidden = true) String authorization,
      @Parameter(hidden = true) String userId);

  @Operation(summary = "Confirm the user's profile image upload")
  ResponseEntity<Void> confirmImageUpload(
      String fileId,
      @Parameter(hidden = true) String requestUuid,
      @Parameter(hidden = true) String authorization,
      @Parameter(hidden = true) String userId);

  @Operation(summary = "Issue a pre-signed url to upload a resume document")
  ResponseEntity<GlobalRestResponse<GenerateUploadResumeResponse>> generateResumeUploadUrl(
      String fileName,
      @Parameter(hidden = true) String requestUuid,
      @Parameter(hidden = true) String authorization,
      @Parameter(hidden = true) String userId);

  @Operation(summary = "Confirm a resume document upload")
  ResponseEntity<Void> confirmResumeUpload(
      String fileId,
      @Parameter(hidden = true) String requestUuid,
      @Parameter(hidden = true) String authorization,
      @Parameter(hidden = true) String userId);

  @Operation(summary = "List the resumes of the authenticated user")
  ResponseEntity<GlobalRestResponse<List<UserResumeResponse>>> getResumes(
      @Parameter(hidden = true) String requestUuid,
      @Parameter(hidden = true) String authorization,
      @Parameter(hidden = true) String userId);

  @Operation(summary = "List the languages a resume can be held in")
  ResponseEntity<GlobalRestResponse<List<SupportedLanguageResponse>>> getSupportedResumeLanguages(
      @Parameter(hidden = true) String requestUuid,
      @Parameter(hidden = true) String authorization,
      @Parameter(hidden = true) String userId);

  @Operation(summary = "Store a resume for the authenticated user")
  ResponseEntity<GlobalRestResponse<UserResumeResponse>> createResume(
      CreateResumeRequest request,
      @Parameter(hidden = true) String requestUuid,
      @Parameter(hidden = true) String authorization,
      @Parameter(hidden = true) String userId);

  @Operation(summary = "Rename one of the authenticated user's resumes")
  ResponseEntity<GlobalRestResponse<UserResumeResponse>> updateResumeAlias(
      String resumeId,
      UpdateResumeAliasRequest request,
      @Parameter(hidden = true) String requestUuid,
      @Parameter(hidden = true) String authorization,
      @Parameter(hidden = true) String userId);

  @Operation(summary = "Delete one of the authenticated user's resumes")
  ResponseEntity<GlobalRestResponse<DeletedResumeResponse>> deleteResume(
      String resumeId,
      @Parameter(hidden = true) String requestUuid,
      @Parameter(hidden = true) String authorization,
      @Parameter(hidden = true) String userId);

  @Operation(summary = "List the job posts the authenticated user applied to")
  ResponseEntity<GlobalRestResponse<PagedResponse<UserApplicationResponse>>> getApplications(
      Integer page,
      Integer size,
      @Parameter(hidden = true) String requestUuid,
      @Parameter(hidden = true) String authorization,
      @Parameter(hidden = true) String userId);

  @Operation(summary = "Suggest what the authenticated user should learn for a job post")
  ResponseEntity<GlobalRestResponse<UpskillingSuggestionResponse>> suggestUpskilling(
      String jobPostId,
      String language,
      @Parameter(hidden = true) String requestUuid,
      @Parameter(hidden = true) String authorization,
      @Parameter(hidden = true) String userId);

  @Operation(summary = "Read the public profile of a user")
  @ApiResponses(@ApiResponse(responseCode = "404", description = "No user holds that id."))
  ResponseEntity<GlobalRestResponse<GetUserProfileResponse>> getUserProfile(
      String userId,
      @Parameter(hidden = true) String requestUuid,
      @Parameter(hidden = true) String authorization,
      @Parameter(hidden = true) String callerId);
}
