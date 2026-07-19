package com.lynq.backend.controller.impl;

import com.lynq.backend.aspect.AuditLog;
import com.lynq.backend.controller.UserController;
import com.lynq.backend.controller.request.CreateUserRequest;
import com.lynq.backend.controller.request.UpdateUserProfileRequest;
import com.lynq.backend.controller.response.CreateUserRestResponse;
import com.lynq.backend.controller.response.GenerateUploadImageRestResponse;
import com.lynq.backend.controller.response.GetUserProfileRestResponse;
import com.lynq.backend.controller.response.GetUserRestResponse;
import com.lynq.backend.controller.response.GetUserResumeRestResponse;
import com.lynq.backend.controller.response.GlobalRestResponse;
import com.lynq.backend.controller.response.UpdateUserProfileRestResponse;
import com.lynq.backend.model.UserEntity;
import com.lynq.backend.security.LynqUserPrincipal;
import com.lynq.backend.service.UserService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/user")
@Validated
public class UserControllerImpl implements UserController {


  private final UserService userService;

  public UserControllerImpl(UserService userService){
    this.userService = userService;
  }

  @Override
  @GetMapping
  @AuditLog
  public ResponseEntity<GlobalRestResponse<GetUserRestResponse>> getUser(@AuthenticationPrincipal LynqUserPrincipal principal) {
    UserEntity user = userService.getUser(principal.getId());
    String profileImageUrl = userService.obtainProfileImagePreSignedUrl(user);
    String companyId = userService.obtainOwnedCompanyId(user);

    GetUserRestResponse response = GetUserRestResponse.builder()
        .id(user.getId())
        .userType(user.getType())
        .fullName(user.getFullName())
        .userProfileImageUrl(profileImageUrl)
        .currentPosition(user.getCurrentPosition())
        .about(user.getAbout())
        .githubUrl(user.getGithubUrl())
        .linkedinUrl(user.getLinkedinUrl())
        .birthDate(user.getBirthDate())
        .createdOn(user.getCreatedOn())
        .companyId(companyId)
        .build();

    return ResponseEntity
        .status(HttpStatus.OK)
        .body(new GlobalRestResponse<>(true, response));
  }

  @Override
  @PostMapping
  @AuditLog
  public ResponseEntity<GlobalRestResponse<CreateUserRestResponse>> createUser(@RequestBody CreateUserRequest request, @AuthenticationPrincipal LynqUserPrincipal principal) {
    UserEntity user = userService.saveNewUser(
        principal.getId(),
        request.getUserType(),
        request.getFullName(),
        request.getCurrentPosition(),
        request.getAbout(),
        request.getGithubUrl(),
        request.getLinkedinUrl(),
        request.getBirthDate());

    String profileImageUrl = userService.obtainProfileImagePreSignedUrl(user);

    CreateUserRestResponse response = CreateUserRestResponse.builder()
        .id(user.getId())
        .userType(user.getType())
        .fullName(user.getFullName())
        .userProfileImageUrl(profileImageUrl)
        .currentPosition(user.getCurrentPosition())
        .about(user.getAbout())
        .githubUrl(user.getGithubUrl())
        .linkedinUrl(user.getLinkedinUrl())
        .birthDate(user.getBirthDate())
        .createdOn(user.getCreatedOn())
        .build();

    return ResponseEntity
        .status(HttpStatus.CREATED)
        .body(new GlobalRestResponse<>(true, response));
  }

  @Override
  @PatchMapping
  @AuditLog
  public ResponseEntity<GlobalRestResponse<UpdateUserProfileRestResponse>> updateUserProfile(@RequestBody UpdateUserProfileRequest request, @AuthenticationPrincipal LynqUserPrincipal principal) {
    UserEntity user = userService.updateUserProfile(principal.getId(), request);

    UpdateUserProfileRestResponse response = UpdateUserProfileRestResponse.builder()
        .id(user.getId())
        .userType(user.getType())
        .fullName(user.getFullName())
        .userProfileImageUrl(user.getProfileImageUrl())
        .currentPosition(user.getCurrentPosition())
        .about(user.getAbout())
        .githubUrl(user.getGithubUrl())
        .linkedinUrl(user.getLinkedinUrl())
        .birthDate(user.getBirthDate())
        .createdOn(user.getCreatedOn())
        .build();

    return ResponseEntity
        .status(HttpStatus.OK)
        .body(new GlobalRestResponse<>(true, response));
  }

  @Override
  @GetMapping("/generate-upload-image")
  @AuditLog
  public ResponseEntity<GlobalRestResponse<GenerateUploadImageRestResponse>> generateUploadImageUrl(
      @RequestParam("file-name") String fileName, @AuthenticationPrincipal LynqUserPrincipal principal) {
    String preSignedUrl = userService.generateProfileImageUploadUrl(principal.getId(), fileName);

    GenerateUploadImageRestResponse response = GenerateUploadImageRestResponse.builder()
        .preSignedUrl(preSignedUrl)
        .build();

    return ResponseEntity
        .status(HttpStatus.OK)
        .body(new GlobalRestResponse<>(true, response));
  }

  @Override
  @GetMapping("/resume")
  @AuditLog
  public ResponseEntity<GlobalRestResponse<List<GetUserResumeRestResponse>>> getUserResumes(
      @AuthenticationPrincipal LynqUserPrincipal principal) {
    List<GetUserResumeRestResponse> resumes = userService.getUserResumes(principal.getId());

    return ResponseEntity
        .status(HttpStatus.OK)
        .body(new GlobalRestResponse<>(true, resumes));
  }

  @Override
  @GetMapping("/{userId}")
  @AuditLog
  public ResponseEntity<GlobalRestResponse<GetUserProfileRestResponse>> getUserProfile(
      @PathVariable String userId) {
    GetUserProfileRestResponse profile = userService.getUserProfile(userId);

    return ResponseEntity
        .status(HttpStatus.OK)
        .body(new GlobalRestResponse<>(true, profile));
  }

}