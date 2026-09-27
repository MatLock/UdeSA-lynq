package com.lynq.bff.service;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import com.lynq.bff.controller.response.GlobalRestResponse;
import com.lynq.bff.exceptions.BadGatewayException;
import com.lynq.bff.exceptions.NotFoundException;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

  private static final String USER_ID = "11111111-1111-1111-1111-111111111111";
  private static final String REQUEST_UUID = "018f9c3a-2b1d-7c4e-9a6f-1e2d3c4b5a99";
  private static final String AUTHORIZATION = "Bearer access-token";
  private static final Caller CALLER = new Caller(USER_ID, REQUEST_UUID, AUTHORIZATION);

  private static final String RESUME_ID = "018f9c3a-2b1d-7c4e-9a6f-1e2d3c4b5a60";
  private static final String FILE_ID = "0195f2c1-3b1a-7c2d-9f31-3f6a5f2c9d41";
  private static final String FILE_NAME = "avatar.png";
  private static final String JOB_POST_ID = "018f9c3a-2b1d-7c4e-9a6f-1e2d3c4b5a61";
  private static final String LANGUAGE = "es";

  @Mock
  private LynqBackendClient lynqBackendClient;

  private UserService userService;

  @BeforeEach
  void setUp() {
    userService = new UserService(lynqBackendClient);
  }

  @Test
  void readsTheAuthenticatedUser() {
    GetUserResponse user = GetUserResponse.builder().id(USER_ID).build();
    when(lynqBackendClient.getUser(REQUEST_UUID, AUTHORIZATION))
        .thenReturn(new GlobalRestResponse<>(true, user));

    assertThat(userService.getUser(CALLER), is(sameInstance(user)));
  }

  @Test
  void createsTheUserWithTheCallersCredentials() {
    CreateUserRequest request = CreateUserRequest.builder().fullName("Jane Doe").build();
    CreateUserResponse created = CreateUserResponse.builder().id(USER_ID).build();
    when(lynqBackendClient.createUser(request, REQUEST_UUID, AUTHORIZATION))
        .thenReturn(new GlobalRestResponse<>(true, created));

    assertThat(userService.createUser(request, CALLER), is(sameInstance(created)));
  }

  @Test
  void updatesTheProfile() {
    UpdateUserProfileRequest request =
        UpdateUserProfileRequest.builder().currentPosition("SRE").build();
    UpdateUserProfileResponse updated = UpdateUserProfileResponse.builder().id(USER_ID).build();
    when(lynqBackendClient.updateUserProfile(request, REQUEST_UUID, AUTHORIZATION))
        .thenReturn(new GlobalRestResponse<>(true, updated));

    assertThat(userService.updateProfile(request, CALLER), is(sameInstance(updated)));
  }

  @Test
  void issuesTheImageUploadUrlForTheFileNameTheCallerGave() {
    GenerateUploadImageResponse upload =
        GenerateUploadImageResponse.builder().fileId(FILE_ID).build();
    when(lynqBackendClient.generateUserImageUploadUrl(FILE_NAME, REQUEST_UUID, AUTHORIZATION))
        .thenReturn(new GlobalRestResponse<>(true, upload));

    assertThat(userService.generateImageUploadUrl(FILE_NAME, CALLER), is(sameInstance(upload)));
  }

  @Test
  void confirmsTheImageUpload() {
    userService.confirmImageUpload(FILE_ID, CALLER);

    verify(lynqBackendClient).confirmUserImageUpload(FILE_ID, REQUEST_UUID, AUTHORIZATION);
  }

  @Test
  void issuesTheResumeUploadUrl() {
    GenerateUploadResumeResponse upload =
        GenerateUploadResumeResponse.builder().fileId(FILE_ID).build();
    when(lynqBackendClient.generateResumeUploadUrl(FILE_NAME, REQUEST_UUID, AUTHORIZATION))
        .thenReturn(new GlobalRestResponse<>(true, upload));

    assertThat(userService.generateResumeUploadUrl(FILE_NAME, CALLER), is(sameInstance(upload)));
  }

  @Test
  void confirmsTheResumeUpload() {
    userService.confirmResumeUpload(FILE_ID, CALLER);

    verify(lynqBackendClient).confirmResumeUpload(FILE_ID, REQUEST_UUID, AUTHORIZATION);
  }

  @Test
  void readsTheCallersResumes() {
    List<UserResumeResponse> resumes =
        List.of(UserResumeResponse.builder().id(RESUME_ID).build());
    when(lynqBackendClient.getUserResumes(REQUEST_UUID, AUTHORIZATION))
        .thenReturn(new GlobalRestResponse<>(true, resumes));

    assertThat(userService.getResumes(CALLER), is(sameInstance(resumes)));
  }

  @Test
  void readsTheSupportedResumeLanguages() {
    List<SupportedLanguageResponse> languages =
        List.of(SupportedLanguageResponse.builder().code("ES").build());
    when(lynqBackendClient.getSupportedResumeLanguages(REQUEST_UUID, AUTHORIZATION))
        .thenReturn(new GlobalRestResponse<>(true, languages));

    assertThat(userService.getSupportedResumeLanguages(CALLER), is(sameInstance(languages)));
  }

  @Test
  void createsAResume() {
    CreateResumeRequest request = CreateResumeRequest.builder().fileId(FILE_ID).build();
    UserResumeResponse created = UserResumeResponse.builder().id(RESUME_ID).build();
    when(lynqBackendClient.createResume(request, REQUEST_UUID, AUTHORIZATION))
        .thenReturn(new GlobalRestResponse<>(true, created));

    assertThat(userService.createResume(request, CALLER), is(sameInstance(created)));
  }

  @Test
  void updatesAResumeAlias() {
    UpdateResumeAliasRequest request =
        UpdateResumeAliasRequest.builder().alias("Backend roles").build();
    UserResumeResponse renamed = UserResumeResponse.builder().id(RESUME_ID).build();
    when(lynqBackendClient.updateResumeAlias(RESUME_ID, request, REQUEST_UUID, AUTHORIZATION))
        .thenReturn(new GlobalRestResponse<>(true, renamed));

    assertThat(userService.updateResumeAlias(RESUME_ID, request, CALLER),
        is(sameInstance(renamed)));
  }

  @Test
  void deletesAResume() {
    DeletedResumeResponse deleted = DeletedResumeResponse.builder().id(RESUME_ID).build();
    when(lynqBackendClient.deleteResume(RESUME_ID, REQUEST_UUID, AUTHORIZATION))
        .thenReturn(new GlobalRestResponse<>(true, deleted));

    assertThat(userService.deleteResume(RESUME_ID, CALLER), is(sameInstance(deleted)));
  }

  @Test
  void readsThePagedApplications() {
    PagedResponse<UserApplicationResponse> page =
        PagedResponse.<UserApplicationResponse>builder().page(2).size(5).build();
    when(lynqBackendClient.getUserApplications(2, 5, REQUEST_UUID, AUTHORIZATION))
        .thenReturn(new GlobalRestResponse<>(true, page));

    assertThat(userService.getApplications(2, 5, CALLER), is(sameInstance(page)));
  }

  @Test
  void readsTheUpskillingSuggestionInTheLanguageAsked() {
    UpskillingSuggestionResponse suggestion =
        UpskillingSuggestionResponse.builder().outcome("READY").build();
    when(lynqBackendClient.suggestUpskillingForUser(
        JOB_POST_ID, LANGUAGE, REQUEST_UUID, AUTHORIZATION))
        .thenReturn(new GlobalRestResponse<>(true, suggestion));

    assertThat(userService.suggestUpskilling(JOB_POST_ID, LANGUAGE, CALLER),
        is(sameInstance(suggestion)));
  }

  @Test
  void readsAnotherUsersPublicProfile() {
    String otherUserId = "22222222-2222-2222-2222-222222222222";
    GetUserProfileResponse profile = GetUserProfileResponse.builder().fullName("John Doe").build();
    when(lynqBackendClient.getUserProfile(otherUserId, REQUEST_UUID, AUTHORIZATION))
        .thenReturn(new GlobalRestResponse<>(true, profile));

    assertThat(userService.getProfile(otherUserId, CALLER), is(sameInstance(profile)));
  }

  @Test
  void keepsTheNotFoundLynqBackendAnswered() {
    when(lynqBackendClient.getUserProfile("nobody", REQUEST_UUID, AUTHORIZATION))
        .thenThrow(FeignErrors.status(404, """
            {"success": false, "reason": "User not found"}"""));

    NotFoundException thrown = assertThrows(NotFoundException.class,
        () -> userService.getProfile("nobody", CALLER));

    assertThat(thrown.getMessage(), is("User not found"));
  }

  @Test
  void answersBadGatewayWhenLynqBackendCannotBeReached() {
    doThrow(FeignErrors.unreachable())
        .when(lynqBackendClient).confirmResumeUpload(FILE_ID, REQUEST_UUID, AUTHORIZATION);

    BadGatewayException thrown = assertThrows(BadGatewayException.class,
        () -> userService.confirmResumeUpload(FILE_ID, CALLER));

    assertThat(thrown.getMessage(), is("The resume upload could not be confirmed"));
  }
}
