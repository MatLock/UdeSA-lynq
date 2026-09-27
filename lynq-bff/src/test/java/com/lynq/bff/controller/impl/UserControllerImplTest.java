package com.lynq.bff.controller.impl;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import com.lynq.bff.service.Caller;
import com.lynq.bff.service.UserService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class UserControllerImplTest {

  private static final String USER_ID = "11111111-1111-1111-1111-111111111111";
  private static final String REQUEST_UUID = "018f9c3a-2b1d-7c4e-9a6f-1e2d3c4b5a99";
  private static final String AUTHORIZATION = "Bearer access-token";
  private static final String RESUME_ID = "018f9c3a-2b1d-7c4e-9a6f-1e2d3c4b5a60";
  private static final String FILE_ID = "0195f2c1-3b1a-7c2d-9f31-3f6a5f2c9d41";
  private static final String FILE_NAME = "avatar.png";
  private static final String JOB_POST_ID = "018f9c3a-2b1d-7c4e-9a6f-1e2d3c4b5a61";

  @Mock
  private UserService userService;

  private UserControllerImpl userController;

  @BeforeEach
  void setUp() {
    userController = new UserControllerImpl(userService);
  }

  /**
   * The caller is always the subject the token was verified against, never anything the browser
   * said it was.
   */
  @Test
  void callsTheServiceAsTheVerifiedCaller() {
    userController.getUser(REQUEST_UUID, AUTHORIZATION, USER_ID);

    ArgumentCaptor<Caller> caller = ArgumentCaptor.forClass(Caller.class);
    verify(userService).getUser(caller.capture());
    assertThat(caller.getValue().userId(), is(USER_ID));
    assertThat(caller.getValue().requestUuid(), is(REQUEST_UUID));
    assertThat(caller.getValue().authorization(), is(AUTHORIZATION));
  }

  @Test
  void answersTheUserWithOk() {
    GetUserResponse user = GetUserResponse.builder().id(USER_ID).build();
    when(userService.getUser(any())).thenReturn(user);

    ResponseEntity<GlobalRestResponse<GetUserResponse>> response =
        userController.getUser(REQUEST_UUID, AUTHORIZATION, USER_ID);

    assertThat(response.getStatusCode(), is(HttpStatus.OK));
    assertThat(response.getBody().getData(), is(sameInstance(user)));
    assertThat(response.getBody().isSuccess(), is(true));
  }

  @Test
  void answersACreatedUserWithCreated() {
    CreateUserRequest request = CreateUserRequest.builder().fullName("Jane Doe").build();
    CreateUserResponse created = CreateUserResponse.builder().id(USER_ID).build();
    when(userService.createUser(eq(request), any())).thenReturn(created);

    ResponseEntity<GlobalRestResponse<CreateUserResponse>> response =
        userController.createUser(request, REQUEST_UUID, AUTHORIZATION, USER_ID);

    assertThat(response.getStatusCode(), is(HttpStatus.CREATED));
    assertThat(response.getBody().getData(), is(sameInstance(created)));
  }

  @Test
  void answersAnUpdatedProfileWithOk() {
    UpdateUserProfileRequest request = UpdateUserProfileRequest.builder().about("hi").build();
    UpdateUserProfileResponse updated = UpdateUserProfileResponse.builder().id(USER_ID).build();
    when(userService.updateProfile(eq(request), any())).thenReturn(updated);

    ResponseEntity<GlobalRestResponse<UpdateUserProfileResponse>> response =
        userController.updateUserProfile(request, REQUEST_UUID, AUTHORIZATION, USER_ID);

    assertThat(response.getStatusCode(), is(HttpStatus.OK));
    assertThat(response.getBody().getData(), is(sameInstance(updated)));
  }

  @Test
  void answersTheImageUploadUrlWithOk() {
    GenerateUploadImageResponse upload =
        GenerateUploadImageResponse.builder().fileId(FILE_ID).build();
    when(userService.generateImageUploadUrl(eq(FILE_NAME), any())).thenReturn(upload);

    ResponseEntity<GlobalRestResponse<GenerateUploadImageResponse>> response =
        userController.generateImageUploadUrl(FILE_NAME, REQUEST_UUID, AUTHORIZATION, USER_ID);

    assertThat(response.getStatusCode(), is(HttpStatus.OK));
    assertThat(response.getBody().getData(), is(sameInstance(upload)));
  }

  @Test
  void answersAConfirmedImageUploadWithNoContentAndNoBody() {
    ResponseEntity<Void> response =
        userController.confirmImageUpload(FILE_ID, REQUEST_UUID, AUTHORIZATION, USER_ID);

    assertThat(response.getStatusCode(), is(HttpStatus.NO_CONTENT));
    assertThat(response.getBody(), is(nullValue()));
    verify(userService).confirmImageUpload(eq(FILE_ID), any());
  }

  @Test
  void answersTheResumeUploadUrlWithOk() {
    GenerateUploadResumeResponse upload =
        GenerateUploadResumeResponse.builder().fileId(FILE_ID).build();
    when(userService.generateResumeUploadUrl(eq(FILE_NAME), any())).thenReturn(upload);

    ResponseEntity<GlobalRestResponse<GenerateUploadResumeResponse>> response =
        userController.generateResumeUploadUrl(FILE_NAME, REQUEST_UUID, AUTHORIZATION, USER_ID);

    assertThat(response.getStatusCode(), is(HttpStatus.OK));
    assertThat(response.getBody().getData(), is(sameInstance(upload)));
  }

  @Test
  void answersAConfirmedResumeUploadWithNoContent() {
    ResponseEntity<Void> response =
        userController.confirmResumeUpload(FILE_ID, REQUEST_UUID, AUTHORIZATION, USER_ID);

    assertThat(response.getStatusCode(), is(HttpStatus.NO_CONTENT));
    verify(userService).confirmResumeUpload(eq(FILE_ID), any());
  }

  @Test
  void answersTheResumesWithOk() {
    List<UserResumeResponse> resumes =
        List.of(UserResumeResponse.builder().id(RESUME_ID).build());
    when(userService.getResumes(any())).thenReturn(resumes);

    ResponseEntity<GlobalRestResponse<List<UserResumeResponse>>> response =
        userController.getResumes(REQUEST_UUID, AUTHORIZATION, USER_ID);

    assertThat(response.getStatusCode(), is(HttpStatus.OK));
    assertThat(response.getBody().getData(), is(sameInstance(resumes)));
  }

  @Test
  void answersTheSupportedLanguagesWithOk() {
    List<SupportedLanguageResponse> languages =
        List.of(SupportedLanguageResponse.builder().code("ES").build());
    when(userService.getSupportedResumeLanguages(any())).thenReturn(languages);

    ResponseEntity<GlobalRestResponse<List<SupportedLanguageResponse>>> response =
        userController.getSupportedResumeLanguages(REQUEST_UUID, AUTHORIZATION, USER_ID);

    assertThat(response.getStatusCode(), is(HttpStatus.OK));
    assertThat(response.getBody().getData(), is(sameInstance(languages)));
  }

  @Test
  void answersACreatedResumeWithCreated() {
    CreateResumeRequest request = CreateResumeRequest.builder().fileId(FILE_ID).build();
    UserResumeResponse created = UserResumeResponse.builder().id(RESUME_ID).build();
    when(userService.createResume(eq(request), any())).thenReturn(created);

    ResponseEntity<GlobalRestResponse<UserResumeResponse>> response =
        userController.createResume(request, REQUEST_UUID, AUTHORIZATION, USER_ID);

    assertThat(response.getStatusCode(), is(HttpStatus.CREATED));
    assertThat(response.getBody().getData(), is(sameInstance(created)));
  }

  @Test
  void answersARenamedResumeWithOk() {
    UpdateResumeAliasRequest request =
        UpdateResumeAliasRequest.builder().alias("Backend roles").build();
    UserResumeResponse renamed = UserResumeResponse.builder().id(RESUME_ID).build();
    when(userService.updateResumeAlias(eq(RESUME_ID), eq(request), any())).thenReturn(renamed);

    ResponseEntity<GlobalRestResponse<UserResumeResponse>> response = userController
        .updateResumeAlias(RESUME_ID, request, REQUEST_UUID, AUTHORIZATION, USER_ID);

    assertThat(response.getStatusCode(), is(HttpStatus.OK));
    assertThat(response.getBody().getData(), is(sameInstance(renamed)));
  }

  @Test
  void answersADeletedResumeWithOk() {
    DeletedResumeResponse deleted = DeletedResumeResponse.builder().id(RESUME_ID).build();
    when(userService.deleteResume(eq(RESUME_ID), any())).thenReturn(deleted);

    ResponseEntity<GlobalRestResponse<DeletedResumeResponse>> response =
        userController.deleteResume(RESUME_ID, REQUEST_UUID, AUTHORIZATION, USER_ID);

    assertThat(response.getStatusCode(), is(HttpStatus.OK));
    assertThat(response.getBody().getData(), is(sameInstance(deleted)));
  }

  @Test
  void answersThePagedApplicationsWithOk() {
    PagedResponse<UserApplicationResponse> page =
        PagedResponse.<UserApplicationResponse>builder().page(2).build();
    when(userService.getApplications(eq(2), eq(5), any())).thenReturn(page);

    ResponseEntity<GlobalRestResponse<PagedResponse<UserApplicationResponse>>> response =
        userController.getApplications(2, 5, REQUEST_UUID, AUTHORIZATION, USER_ID);

    assertThat(response.getStatusCode(), is(HttpStatus.OK));
    assertThat(response.getBody().getData(), is(sameInstance(page)));
  }

  @Test
  void answersTheUpskillingSuggestionWithOk() {
    UpskillingSuggestionResponse suggestion =
        UpskillingSuggestionResponse.builder().outcome("READY").build();
    when(userService.suggestUpskilling(eq(JOB_POST_ID), eq("es"), any())).thenReturn(suggestion);

    ResponseEntity<GlobalRestResponse<UpskillingSuggestionResponse>> response = userController
        .suggestUpskilling(JOB_POST_ID, "es", REQUEST_UUID, AUTHORIZATION, USER_ID);

    assertThat(response.getStatusCode(), is(HttpStatus.OK));
    assertThat(response.getBody().getData(), is(sameInstance(suggestion)));
  }

  /**
   * The public profile is read by id, and the caller stays the token's subject: the two ids are
   * different arguments and must not be crossed.
   */
  @Test
  void readsThePublicProfileOfTheUserAskedForNotOfTheCaller() {
    String otherUserId = "22222222-2222-2222-2222-222222222222";
    GetUserProfileResponse profile = GetUserProfileResponse.builder().fullName("John").build();
    ArgumentCaptor<Caller> caller = ArgumentCaptor.forClass(Caller.class);
    when(userService.getProfile(eq(otherUserId), any())).thenReturn(profile);

    ResponseEntity<GlobalRestResponse<GetUserProfileResponse>> response =
        userController.getUserProfile(otherUserId, REQUEST_UUID, AUTHORIZATION, USER_ID);

    assertThat(response.getStatusCode(), is(HttpStatus.OK));
    assertThat(response.getBody().getData(), is(sameInstance(profile)));
    verify(userService).getProfile(eq(otherUserId), caller.capture());
    assertThat(caller.getValue().userId(), is(USER_ID));
  }
}
