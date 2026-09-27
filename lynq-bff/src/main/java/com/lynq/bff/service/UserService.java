package com.lynq.bff.service;

import com.lynq.bff.client.LynqBackendClient;
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
import java.util.List;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

@Service
@Log4j2
public class UserService {

  private static final String USER_UNREADABLE = "The user could not be read";
  private static final String USER_NOT_CREATED = "The user could not be created";
  private static final String PROFILE_NOT_UPDATED = "The user profile could not be updated";
  private static final String IMAGE_URL_NOT_ISSUED = "The image upload url could not be issued";
  private static final String IMAGE_NOT_CONFIRMED = "The image upload could not be confirmed";
  private static final String RESUME_URL_NOT_ISSUED = "The resume upload url could not be issued";
  private static final String RESUME_NOT_CONFIRMED = "The resume upload could not be confirmed";
  private static final String RESUMES_UNREADABLE = "The user resumes could not be read";
  private static final String LANGUAGES_UNREADABLE = "The supported languages could not be read";
  private static final String RESUME_NOT_CREATED = "The resume could not be created";
  private static final String ALIAS_NOT_SAVED = "The resume alias could not be saved";
  private static final String RESUME_NOT_DELETED = "The resume could not be deleted";
  private static final String APPLICATIONS_UNREADABLE = "The applications could not be read";
  private static final String SUGGESTION_UNREADABLE = "The upskilling suggestion could not be read";
  private static final String PROFILE_UNREADABLE = "The user profile could not be read";

  private final LynqBackendClient lynqBackendClient;

  public UserService(LynqBackendClient lynqBackendClient) {
    this.lynqBackendClient = lynqBackendClient;
  }

  public GetUserResponse getUser(Caller caller) {
    return DownstreamErrors.call(
        () -> lynqBackendClient.getUser(caller.requestUuid(), caller.authorization()).getData(),
        USER_UNREADABLE);
  }

  public CreateUserResponse createUser(CreateUserRequest request, Caller caller) {
    log.info("message= Creating user, user_id={}", caller.userId());

    return DownstreamErrors.call(
        () -> lynqBackendClient
            .createUser(request, caller.requestUuid(), caller.authorization())
            .getData(),
        USER_NOT_CREATED);
  }

  public UpdateUserProfileResponse updateProfile(UpdateUserProfileRequest request, Caller caller) {
    log.info("message= Updating user profile, user_id={}", caller.userId());

    return DownstreamErrors.call(
        () -> lynqBackendClient
            .updateUserProfile(request, caller.requestUuid(), caller.authorization())
            .getData(),
        PROFILE_NOT_UPDATED);
  }

  public GenerateUploadImageResponse generateImageUploadUrl(String fileName, Caller caller) {
    return DownstreamErrors.call(
        () -> lynqBackendClient
            .generateUserImageUploadUrl(fileName, caller.requestUuid(), caller.authorization())
            .getData(),
        IMAGE_URL_NOT_ISSUED);
  }

  public void confirmImageUpload(String fileId, Caller caller) {
    log.info("message= Confirming user image upload, user_id={}, file_id={}",
        caller.userId(), fileId);

    DownstreamErrors.run(
        () -> lynqBackendClient
            .confirmUserImageUpload(fileId, caller.requestUuid(), caller.authorization()),
        IMAGE_NOT_CONFIRMED);
  }

  public GenerateUploadResumeResponse generateResumeUploadUrl(String fileName, Caller caller) {
    return DownstreamErrors.call(
        () -> lynqBackendClient
            .generateResumeUploadUrl(fileName, caller.requestUuid(), caller.authorization())
            .getData(),
        RESUME_URL_NOT_ISSUED);
  }

  public void confirmResumeUpload(String fileId, Caller caller) {
    log.info("message= Confirming resume upload, user_id={}, file_id={}", caller.userId(), fileId);

    DownstreamErrors.run(
        () -> lynqBackendClient
            .confirmResumeUpload(fileId, caller.requestUuid(), caller.authorization()),
        RESUME_NOT_CONFIRMED);
  }

  public List<UserResumeResponse> getResumes(Caller caller) {
    return DownstreamErrors.call(
        () -> lynqBackendClient
            .getUserResumes(caller.requestUuid(), caller.authorization())
            .getData(),
        RESUMES_UNREADABLE);
  }

  public List<SupportedLanguageResponse> getSupportedResumeLanguages(Caller caller) {
    return DownstreamErrors.call(
        () -> lynqBackendClient
            .getSupportedResumeLanguages(caller.requestUuid(), caller.authorization())
            .getData(),
        LANGUAGES_UNREADABLE);
  }

  public UserResumeResponse createResume(CreateResumeRequest request, Caller caller) {
    log.info("message= Creating resume, user_id={}, file_id={}",
        caller.userId(), request.getFileId());

    return DownstreamErrors.call(
        () -> lynqBackendClient
            .createResume(request, caller.requestUuid(), caller.authorization())
            .getData(),
        RESUME_NOT_CREATED);
  }

  public UserResumeResponse updateResumeAlias(String resumeId, UpdateResumeAliasRequest request,
                                              Caller caller) {
    log.info("message= Updating resume alias, user_id={}, resume_id={}",
        caller.userId(), resumeId);

    return DownstreamErrors.call(
        () -> lynqBackendClient
            .updateResumeAlias(resumeId, request, caller.requestUuid(), caller.authorization())
            .getData(),
        ALIAS_NOT_SAVED);
  }

  public DeletedResumeResponse deleteResume(String resumeId, Caller caller) {
    log.info("message= Deleting resume, user_id={}, resume_id={}", caller.userId(), resumeId);

    return DownstreamErrors.call(
        () -> lynqBackendClient
            .deleteResume(resumeId, caller.requestUuid(), caller.authorization())
            .getData(),
        RESUME_NOT_DELETED);
  }

  public PagedResponse<UserApplicationResponse> getApplications(Integer page, Integer size,
                                                                Caller caller) {
    return DownstreamErrors.call(
        () -> lynqBackendClient
            .getUserApplications(page, size, caller.requestUuid(), caller.authorization())
            .getData(),
        APPLICATIONS_UNREADABLE);
  }

  public UpskillingSuggestionResponse suggestUpskilling(String jobPostId, String language,
                                                        Caller caller) {
    return DownstreamErrors.call(
        () -> lynqBackendClient
            .suggestUpskillingForUser(jobPostId, language, caller.requestUuid(),
                caller.authorization())
            .getData(),
        SUGGESTION_UNREADABLE);
  }

  public GetUserProfileResponse getProfile(String userId, Caller caller) {
    return DownstreamErrors.call(
        () -> lynqBackendClient
            .getUserProfile(userId, caller.requestUuid(), caller.authorization())
            .getData(),
        PROFILE_UNREADABLE);
  }
}
