package com.lynq.bff.controller.impl;

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
import com.lynq.bff.controller.UserController;
import com.lynq.bff.controller.response.GlobalRestResponse;
import com.lynq.bff.filter.JwtSignatureFilter;
import com.lynq.bff.service.Caller;
import com.lynq.bff.service.UserService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/user")
public class UserControllerImpl implements UserController {

  private static final String REQUEST_UUID_HEADER = "lynq-request-uuid";
  private static final String AUTHORIZATION_HEADER = "Authorization";

  private final UserService userService;

  public UserControllerImpl(UserService userService) {
    this.userService = userService;
  }

  @Override
  @GetMapping
  public ResponseEntity<GlobalRestResponse<GetUserResponse>> getUser(
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid,
      @RequestHeader(AUTHORIZATION_HEADER) String authorization,
      @RequestAttribute(JwtSignatureFilter.VERIFIED_USER_ID) String userId) {
    GetUserResponse user = userService.getUser(caller(userId, requestUuid, authorization));

    return ResponseEntity
        .status(HttpStatus.OK)
        .body(new GlobalRestResponse<>(true, user));
  }

  @Override
  @PostMapping
  public ResponseEntity<GlobalRestResponse<CreateUserResponse>> createUser(
      @RequestBody CreateUserRequest request,
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid,
      @RequestHeader(AUTHORIZATION_HEADER) String authorization,
      @RequestAttribute(JwtSignatureFilter.VERIFIED_USER_ID) String userId) {
    CreateUserResponse created =
        userService.createUser(request, caller(userId, requestUuid, authorization));

    return ResponseEntity
        .status(HttpStatus.CREATED)
        .body(new GlobalRestResponse<>(true, created));
  }

  @Override
  @PatchMapping
  public ResponseEntity<GlobalRestResponse<UpdateUserProfileResponse>> updateUserProfile(
      @RequestBody UpdateUserProfileRequest request,
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid,
      @RequestHeader(AUTHORIZATION_HEADER) String authorization,
      @RequestAttribute(JwtSignatureFilter.VERIFIED_USER_ID) String userId) {
    UpdateUserProfileResponse updated =
        userService.updateProfile(request, caller(userId, requestUuid, authorization));

    return ResponseEntity
        .status(HttpStatus.OK)
        .body(new GlobalRestResponse<>(true, updated));
  }

  @Override
  @GetMapping("/generate-upload-image")
  public ResponseEntity<GlobalRestResponse<GenerateUploadImageResponse>> generateImageUploadUrl(
      @RequestParam("file-name") String fileName,
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid,
      @RequestHeader(AUTHORIZATION_HEADER) String authorization,
      @RequestAttribute(JwtSignatureFilter.VERIFIED_USER_ID) String userId) {
    GenerateUploadImageResponse upload =
        userService.generateImageUploadUrl(fileName, caller(userId, requestUuid, authorization));

    return ResponseEntity
        .status(HttpStatus.OK)
        .body(new GlobalRestResponse<>(true, upload));
  }

  @Override
  @PostMapping("/confirm-upload-image")
  public ResponseEntity<Void> confirmImageUpload(
      @RequestParam("file-id") String fileId,
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid,
      @RequestHeader(AUTHORIZATION_HEADER) String authorization,
      @RequestAttribute(JwtSignatureFilter.VERIFIED_USER_ID) String userId) {
    userService.confirmImageUpload(fileId, caller(userId, requestUuid, authorization));

    return ResponseEntity.noContent().build();
  }

  @Override
  @GetMapping("/generate-upload-resume")
  public ResponseEntity<GlobalRestResponse<GenerateUploadResumeResponse>> generateResumeUploadUrl(
      @RequestParam("file-name") String fileName,
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid,
      @RequestHeader(AUTHORIZATION_HEADER) String authorization,
      @RequestAttribute(JwtSignatureFilter.VERIFIED_USER_ID) String userId) {
    GenerateUploadResumeResponse upload =
        userService.generateResumeUploadUrl(fileName, caller(userId, requestUuid, authorization));

    return ResponseEntity
        .status(HttpStatus.OK)
        .body(new GlobalRestResponse<>(true, upload));
  }

  @Override
  @PostMapping("/confirm-upload-resume")
  public ResponseEntity<Void> confirmResumeUpload(
      @RequestParam("file-id") String fileId,
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid,
      @RequestHeader(AUTHORIZATION_HEADER) String authorization,
      @RequestAttribute(JwtSignatureFilter.VERIFIED_USER_ID) String userId) {
    userService.confirmResumeUpload(fileId, caller(userId, requestUuid, authorization));

    return ResponseEntity.noContent().build();
  }

  @Override
  @GetMapping("/resume")
  public ResponseEntity<GlobalRestResponse<List<UserResumeResponse>>> getResumes(
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid,
      @RequestHeader(AUTHORIZATION_HEADER) String authorization,
      @RequestAttribute(JwtSignatureFilter.VERIFIED_USER_ID) String userId) {
    List<UserResumeResponse> resumes =
        userService.getResumes(caller(userId, requestUuid, authorization));

    return ResponseEntity
        .status(HttpStatus.OK)
        .body(new GlobalRestResponse<>(true, resumes));
  }

  @Override
  @GetMapping("/resume/languages")
  public ResponseEntity<GlobalRestResponse<List<SupportedLanguageResponse>>>
      getSupportedResumeLanguages(
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid,
      @RequestHeader(AUTHORIZATION_HEADER) String authorization,
      @RequestAttribute(JwtSignatureFilter.VERIFIED_USER_ID) String userId) {
    List<SupportedLanguageResponse> languages =
        userService.getSupportedResumeLanguages(caller(userId, requestUuid, authorization));

    return ResponseEntity
        .status(HttpStatus.OK)
        .body(new GlobalRestResponse<>(true, languages));
  }

  @Override
  @PostMapping("/resume")
  public ResponseEntity<GlobalRestResponse<UserResumeResponse>> createResume(
      @RequestBody CreateResumeRequest request,
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid,
      @RequestHeader(AUTHORIZATION_HEADER) String authorization,
      @RequestAttribute(JwtSignatureFilter.VERIFIED_USER_ID) String userId) {
    UserResumeResponse created =
        userService.createResume(request, caller(userId, requestUuid, authorization));

    return ResponseEntity
        .status(HttpStatus.CREATED)
        .body(new GlobalRestResponse<>(true, created));
  }

  @Override
  @PutMapping("/resume/{resumeId}/alias")
  public ResponseEntity<GlobalRestResponse<UserResumeResponse>> updateResumeAlias(
      @PathVariable String resumeId,
      @RequestBody UpdateResumeAliasRequest request,
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid,
      @RequestHeader(AUTHORIZATION_HEADER) String authorization,
      @RequestAttribute(JwtSignatureFilter.VERIFIED_USER_ID) String userId) {
    UserResumeResponse renamed = userService.updateResumeAlias(
        resumeId, request, caller(userId, requestUuid, authorization));

    return ResponseEntity
        .status(HttpStatus.OK)
        .body(new GlobalRestResponse<>(true, renamed));
  }

  @Override
  @DeleteMapping("/resume/{resumeId}")
  public ResponseEntity<GlobalRestResponse<DeletedResumeResponse>> deleteResume(
      @PathVariable String resumeId,
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid,
      @RequestHeader(AUTHORIZATION_HEADER) String authorization,
      @RequestAttribute(JwtSignatureFilter.VERIFIED_USER_ID) String userId) {
    DeletedResumeResponse deleted =
        userService.deleteResume(resumeId, caller(userId, requestUuid, authorization));

    return ResponseEntity
        .status(HttpStatus.OK)
        .body(new GlobalRestResponse<>(true, deleted));
  }

  @Override
  @GetMapping("/application")
  public ResponseEntity<GlobalRestResponse<PagedResponse<UserApplicationResponse>>> getApplications(
      @RequestParam(defaultValue = "0") Integer page,
      @RequestParam(defaultValue = "10") Integer size,
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid,
      @RequestHeader(AUTHORIZATION_HEADER) String authorization,
      @RequestAttribute(JwtSignatureFilter.VERIFIED_USER_ID) String userId) {
    PagedResponse<UserApplicationResponse> applications =
        userService.getApplications(page, size, caller(userId, requestUuid, authorization));

    return ResponseEntity
        .status(HttpStatus.OK)
        .body(new GlobalRestResponse<>(true, applications));
  }

  @Override
  @GetMapping("/upskilling-suggestion/{jobPostId}")
  public ResponseEntity<GlobalRestResponse<UpskillingSuggestionResponse>> suggestUpskilling(
      @PathVariable String jobPostId,
      @RequestParam(defaultValue = "en") String language,
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid,
      @RequestHeader(AUTHORIZATION_HEADER) String authorization,
      @RequestAttribute(JwtSignatureFilter.VERIFIED_USER_ID) String userId) {
    UpskillingSuggestionResponse suggestion = userService.suggestUpskilling(
        jobPostId, language, caller(userId, requestUuid, authorization));

    return ResponseEntity
        .status(HttpStatus.OK)
        .body(new GlobalRestResponse<>(true, suggestion));
  }

  @Override
  @GetMapping("/{userId}")
  public ResponseEntity<GlobalRestResponse<GetUserProfileResponse>> getUserProfile(
      @PathVariable String userId,
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid,
      @RequestHeader(AUTHORIZATION_HEADER) String authorization,
      @RequestAttribute(JwtSignatureFilter.VERIFIED_USER_ID) String callerId) {
    GetUserProfileResponse profile =
        userService.getProfile(userId, caller(callerId, requestUuid, authorization));

    return ResponseEntity
        .status(HttpStatus.OK)
        .body(new GlobalRestResponse<>(true, profile));
  }

  private static Caller caller(String userId, String requestUuid, String authorization) {
    return new Caller(userId, requestUuid, authorization);
  }
}
