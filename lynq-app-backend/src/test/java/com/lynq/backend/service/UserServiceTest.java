package com.lynq.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynq.backend.controller.request.UpdateUserProfileRequest;
import com.lynq.backend.controller.response.GetUserResumeRestResponse;
import com.lynq.backend.enums.JobStatus;
import com.lynq.backend.enums.Language;
import com.lynq.backend.enums.UserType;
import com.lynq.backend.exceptions.BadRequestException;
import com.lynq.backend.exceptions.NotFoundException;
import com.lynq.backend.model.CompanyEntity;
import com.lynq.backend.model.JobPostEntity;
import com.lynq.backend.model.UserEntity;
import com.lynq.backend.model.UserResumeEntity;
import com.lynq.backend.controller.response.GetUserProfileRestResponse;
import com.lynq.backend.repository.CompanyRepository;
import com.lynq.backend.repository.JobPostRepository;
import com.lynq.backend.repository.UserRepository;
import com.lynq.backend.repository.UserResumeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

  private static final String USER_ID = "550e8400-e29b-41d4-a716-446655440000";
  private static final UserType USER_TYPE = UserType.CANDIDATE;
  private static final String FULL_NAME = "Jane Doe";
  private static final String PROFILE_IMAGE_URL = "https://cdn.lynq.com/avatars/jane.png";
  private static final String CURRENT_POSITION = "Backend Engineer";
  private static final String ABOUT = "Java developer focused on distributed systems.";
  private static final String GITHUB_URL = "https://github.com/janedoe";
  private static final String LINKEDIN_URL = "https://linkedin.com/in/janedoe";
  private static final LocalDate BIRTH_DATE = LocalDate.of(1995, 4, 12);

  private static final String UPDATED_FULL_NAME = "Jane Q. Doe";
  private static final String UPDATED_CURRENT_POSITION = "Staff Engineer";

  private static final String FILE_NAME = "avatar.png";
  private static final String S3_PATH = "lynq/users/" + USER_ID + "/profile/" + FILE_NAME;
  private static final String PREVIOUS_S3_PATH = "lynq/users/" + USER_ID + "/profile/old-avatar.png";
  private static final String PRE_SIGNED_URL =
      "https://lynq-bucket.s3.amazonaws.com/" + S3_PATH + "?X-Amz-Signature=abc";

  private static final String RESUME_ID = "resume-1";
  private static final String RESUME_NAME = "Jane Doe - Backend";
  private static final Language RESUME_LANGUAGE = Language.EN;
  private static final LocalDate RESUME_CREATED_ON = LocalDate.of(2026, 7, 17);
  private static final String RESUME_JSON = "{\"summary\":\"Backend engineer\",\"years\":8}";
  private static final String RESUME_STORAGE_PATH = "lynq/users/" + USER_ID + "/resume/cv.pdf";
  private static final String RESUME_PDF_URL = "https://presigned/cv.pdf";

  private static final String COMPANY_ID = "company-1";
  private static final String COMPANY_NAME = "Lynq";
  private static final String COMPANY_IMAGE_PATH = "lynq/companies/" + COMPANY_ID + "/profile/logo.png";
  private static final String COMPANY_IMAGE_URL = "https://presigned/company-logo.png";
  private static final String JOB_ID = "job-1";
  private static final String JOB_TITLE = "Senior Backend Engineer";
  private static final String JOB_DESCRIPTION = "Build and scale the Lynq hiring platform.";

  private static final String USER_NOT_FOUND = "User '" + USER_ID + "' not found";
  private static final String ONLY_CANDIDATE_USERS_CAN_ACCESS_RESUMES =
      "Only users of type CANDIDATE can access resumes";

  @Mock
  private UserRepository userRepository;

  @Mock
  private UserResumeRepository userResumeRepository;

  @Mock
  private CompanyRepository companyRepository;

  @Mock
  private JobPostRepository jobPostRepository;

  @Mock
  private UpdateUserProfileRequest updateRequest;

  @Mock
  private StorageService storageService;

  private final ObjectMapper objectMapper = new ObjectMapper();

  private UserService userService;

  @BeforeEach
  void setUp() {
    userService = new UserService(userRepository, userResumeRepository, companyRepository,
        jobPostRepository, storageService, objectMapper);
  }

  @Test
  void saveNewUserPersistsEntityBuiltFromArguments() {
    when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
    ArgumentCaptor<UserEntity> userCaptor = ArgumentCaptor.forClass(UserEntity.class);

    userService.saveNewUser(USER_ID, USER_TYPE, FULL_NAME, CURRENT_POSITION, ABOUT,
        GITHUB_URL, LINKEDIN_URL, BIRTH_DATE);

    verify(userRepository).save(userCaptor.capture());
    UserEntity saved = userCaptor.getValue();
    assertThat(saved.getId(), is(USER_ID));
    assertThat(saved.getType(), is(USER_TYPE));
    assertThat(saved.getFullName(), is(FULL_NAME));
    assertThat(saved.getProfileImageUrl(), is(org.hamcrest.Matchers.nullValue()));
    assertThat(saved.getCurrentPosition(), is(CURRENT_POSITION));
    assertThat(saved.getAbout(), is(ABOUT));
    assertThat(saved.getGithubUrl(), is(GITHUB_URL));
    assertThat(saved.getLinkedinUrl(), is(LINKEDIN_URL));
    assertThat(saved.getBirthDate(), is(BIRTH_DATE));
  }

  @Test
  void saveNewUserStampsCreatedOnWithToday() {
    when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
    ArgumentCaptor<UserEntity> userCaptor = ArgumentCaptor.forClass(UserEntity.class);

    userService.saveNewUser(USER_ID, USER_TYPE, FULL_NAME, CURRENT_POSITION, ABOUT,
        GITHUB_URL, LINKEDIN_URL, BIRTH_DATE);

    verify(userRepository).save(userCaptor.capture());
    assertThat(userCaptor.getValue().getCreatedOn(), is(LocalDate.now()));
  }

  @Test
  void saveNewUserReturnsEntityProducedByRepository() {
    UserEntity persisted = UserEntity.builder().id(USER_ID).build();
    when(userRepository.save(any(UserEntity.class))).thenReturn(persisted);

    UserEntity result = userService.saveNewUser(USER_ID, USER_TYPE, FULL_NAME,
        CURRENT_POSITION, ABOUT, GITHUB_URL, LINKEDIN_URL, BIRTH_DATE);

    assertThat(result, is(sameInstance(persisted)));
  }

  @Test
  void getUserReturnsEntityFoundByRepository() {
    UserEntity existing = existingUser();
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(existing));

    UserEntity result = userService.getUser(USER_ID);

    assertThat(result, is(sameInstance(existing)));
  }

  @Test
  void obtainProfileImagePreSignedUrlReturnsPreSignedUrlWhenImagePresent() {
    UserEntity existing = existingUser();
    existing.setProfileImageUrl(S3_PATH);
    when(storageService.obtainUserProfilePreSignedUrl(existing)).thenReturn(PRE_SIGNED_URL);

    String result = userService.obtainProfileImagePreSignedUrl(existing);

    assertThat(result, is(PRE_SIGNED_URL));
  }

  @Test
  void obtainProfileImagePreSignedUrlReturnsNullWhenImageAbsent() {
    UserEntity existing = existingUser();
    existing.setProfileImageUrl(null);

    String result = userService.obtainProfileImagePreSignedUrl(existing);

    assertThat(result, is(org.hamcrest.Matchers.nullValue()));
    verify(storageService, never()).obtainUserProfilePreSignedUrl(any());
  }

  @Test
  void getUserThrowsNotFoundWhenUserDoesNotExist() {
    when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

    assertThrows(NotFoundException.class, () -> userService.getUser(USER_ID));
  }

  @Test
  void updateUserProfileAppliesSuppliedFieldsToExistingUser() {
    UserEntity existing = existingUser();
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(existing));
    when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
    when(updateRequest.getFullName()).thenReturn(UPDATED_FULL_NAME);
    when(updateRequest.getCurrentPosition()).thenReturn(UPDATED_CURRENT_POSITION);
    ArgumentCaptor<UserEntity> userCaptor = ArgumentCaptor.forClass(UserEntity.class);

    userService.updateUserProfile(USER_ID, updateRequest);

    verify(userRepository).save(userCaptor.capture());
    UserEntity saved = userCaptor.getValue();
    assertThat(saved.getFullName(), is(UPDATED_FULL_NAME));
    assertThat(saved.getCurrentPosition(), is(UPDATED_CURRENT_POSITION));
  }

  @Test
  void updateUserProfileLeavesOmittedFieldsUnchanged() {
    UserEntity existing = existingUser();
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(existing));
    when(userRepository.save(any(UserEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
    when(updateRequest.getFullName()).thenReturn(UPDATED_FULL_NAME);
    ArgumentCaptor<UserEntity> userCaptor = ArgumentCaptor.forClass(UserEntity.class);

    userService.updateUserProfile(USER_ID, updateRequest);

    verify(userRepository).save(userCaptor.capture());
    UserEntity saved = userCaptor.getValue();
    assertThat(saved.getFullName(), is(UPDATED_FULL_NAME));
    assertThat(saved.getCurrentPosition(), is(CURRENT_POSITION));
    assertThat(saved.getAbout(), is(ABOUT));
    assertThat(saved.getGithubUrl(), is(GITHUB_URL));
    assertThat(saved.getLinkedinUrl(), is(LINKEDIN_URL));
    assertThat(saved.getProfileImageUrl(), is(PROFILE_IMAGE_URL));
    assertThat(saved.getBirthDate(), is(BIRTH_DATE));
  }

  @Test
  void updateUserProfileReturnsEntityProducedByRepository() {
    UserEntity existing = existingUser();
    UserEntity persisted = UserEntity.builder().id(USER_ID).build();
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(existing));
    when(userRepository.save(any(UserEntity.class))).thenReturn(persisted);

    UserEntity result = userService.updateUserProfile(USER_ID, updateRequest);

    assertThat(result, is(sameInstance(persisted)));
  }

  @Test
  void updateUserProfileThrowsNotFoundWhenUserDoesNotExist() {
    when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

    assertThrows(NotFoundException.class,
        () -> userService.updateUserProfile(USER_ID, updateRequest));
    verify(userRepository, never()).save(any());
  }

  @Test
  void generateProfileImageUploadUrlPersistsS3PathAndReturnsPreSignedUrl() {
    UserEntity existing = existingUser();
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(existing));
    when(storageService.createUserProfilePreSignedUrl(existing, FILE_NAME))
        .thenReturn(new PreSignedUploadUrl(S3_PATH, PRE_SIGNED_URL));
    ArgumentCaptor<UserEntity> userCaptor = ArgumentCaptor.forClass(UserEntity.class);

    String result = userService.generateProfileImageUploadUrl(USER_ID, FILE_NAME);

    assertThat(result, is(PRE_SIGNED_URL));
    verify(userRepository).save(userCaptor.capture());
    assertThat(userCaptor.getValue().getProfileImageUrl(), is(S3_PATH));
  }

  @Test
  void generateProfileImageUploadUrlDeletesPreviousObjectWhenPathChanges() {
    UserEntity existing = existingUser();
    existing.setProfileImageUrl(PREVIOUS_S3_PATH);
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(existing));
    when(storageService.createUserProfilePreSignedUrl(existing, FILE_NAME))
        .thenReturn(new PreSignedUploadUrl(S3_PATH, PRE_SIGNED_URL));

    userService.generateProfileImageUploadUrl(USER_ID, FILE_NAME);

    verify(storageService).deleteObject(PREVIOUS_S3_PATH);
  }

  @Test
  void generateProfileImageUploadUrlDoesNotDeleteWhenNoPreviousObjectExists() {
    UserEntity existing = existingUser();
    existing.setProfileImageUrl(null);
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(existing));
    when(storageService.createUserProfilePreSignedUrl(existing, FILE_NAME))
        .thenReturn(new PreSignedUploadUrl(S3_PATH, PRE_SIGNED_URL));

    userService.generateProfileImageUploadUrl(USER_ID, FILE_NAME);

    verify(storageService, never()).deleteObject(any());
  }

  @Test
  void generateProfileImageUploadUrlDoesNotDeleteWhenPathIsUnchanged() {
    UserEntity existing = existingUser();
    existing.setProfileImageUrl(S3_PATH);
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(existing));
    when(storageService.createUserProfilePreSignedUrl(existing, FILE_NAME))
        .thenReturn(new PreSignedUploadUrl(S3_PATH, PRE_SIGNED_URL));

    userService.generateProfileImageUploadUrl(USER_ID, FILE_NAME);

    verify(storageService, never()).deleteObject(any());
  }

  @Test
  void generateProfileImageUploadUrlThrowsNotFoundWhenUserDoesNotExist() {
    when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

    assertThrows(NotFoundException.class,
        () -> userService.generateProfileImageUploadUrl(USER_ID, FILE_NAME));
    verify(userRepository, never()).save(any());
  }

  @Test
  void getUserResumesMapsEntitiesWithParsedJsonAndPresignedPdfUrl() {
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(candidate()));
    when(userResumeRepository.findByUserId(USER_ID))
        .thenReturn(List.of(resume(RESUME_JSON, RESUME_STORAGE_PATH)));
    when(storageService.obtainProfilePreSignedUrl(RESUME_STORAGE_PATH)).thenReturn(RESUME_PDF_URL);

    List<GetUserResumeRestResponse> result = userService.getUserResumes(USER_ID);

    assertThat(result, hasSize(1));
    GetUserResumeRestResponse response = result.get(0);
    assertThat(response.getId(), is(RESUME_ID));
    assertThat(response.getName(), is(RESUME_NAME));
    assertThat(response.getLanguage(), is(RESUME_LANGUAGE));
    assertThat(response.getCreatedOn(), is(RESUME_CREATED_ON));
    assertThat(response.getPdfUrl(), is(RESUME_PDF_URL));
    @SuppressWarnings("unchecked")
    Map<String, Object> resumeJson = (Map<String, Object>) response.getResume();
    assertThat(resumeJson.get("summary"), is("Backend engineer"));
    assertThat(resumeJson.get("years"), is(8));
  }

  @Test
  void getUserResumesLeavesPdfUrlNullWhenStoragePathIsBlank() {
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(candidate()));
    when(userResumeRepository.findByUserId(USER_ID))
        .thenReturn(List.of(resume(RESUME_JSON, null)));

    GetUserResumeRestResponse response = userService.getUserResumes(USER_ID).get(0);

    assertThat(response.getPdfUrl(), is(nullValue()));
    verify(storageService, never()).obtainProfilePreSignedUrl(any());
  }

  @Test
  void getUserResumesLeavesResumeNullWhenJsonIsBlank() {
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(candidate()));
    when(userResumeRepository.findByUserId(USER_ID))
        .thenReturn(List.of(resume(null, RESUME_STORAGE_PATH)));
    when(storageService.obtainProfilePreSignedUrl(RESUME_STORAGE_PATH)).thenReturn(RESUME_PDF_URL);

    GetUserResumeRestResponse response = userService.getUserResumes(USER_ID).get(0);

    assertThat(response.getResume(), is(nullValue()));
  }

  @Test
  void getUserResumesReturnsEmptyWhenCandidateHasNoResumes() {
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(candidate()));
    when(userResumeRepository.findByUserId(USER_ID)).thenReturn(List.of());

    assertThat(userService.getUserResumes(USER_ID), is(empty()));
  }

  @Test
  void getUserResumesThrowsBadRequestWhenUserIsNotCandidate() {
    UserEntity company = UserEntity.builder().id(USER_ID).type(UserType.COMPANY).build();
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(company));

    BadRequestException exception = assertThrows(BadRequestException.class,
        () -> userService.getUserResumes(USER_ID));
    assertThat(exception.getMessage(), is(ONLY_CANDIDATE_USERS_CAN_ACCESS_RESUMES));
    verify(userResumeRepository, never()).findByUserId(any());
  }

  @Test
  void getUserResumesThrowsNotFoundWhenUserDoesNotExist() {
    when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

    NotFoundException exception = assertThrows(NotFoundException.class,
        () -> userService.getUserResumes(USER_ID));
    assertThat(exception.getMessage(), is(USER_NOT_FOUND));
    verify(userResumeRepository, never()).findByUserId(any());
  }

  @Test
  void getUserProfileMapsProfileFieldsAndPresignedImageForCandidate() {
    UserEntity existing = existingUser();
    existing.setProfileImageUrl(S3_PATH);
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(existing));
    when(storageService.obtainUserProfilePreSignedUrl(existing)).thenReturn(PRE_SIGNED_URL);

    GetUserProfileRestResponse profile = userService.getUserProfile(USER_ID);

    assertThat(profile.getFullName(), is(FULL_NAME));
    assertThat(profile.getProfileImageUrl(), is(PRE_SIGNED_URL));
    assertThat(profile.getCurrentPosition(), is(CURRENT_POSITION));
    assertThat(profile.getAbout(), is(ABOUT));
    assertThat(profile.getGithubUrl(), is(GITHUB_URL));
    assertThat(profile.getLinkedinUrl(), is(LINKEDIN_URL));
    assertThat(profile.getCompany(), is(nullValue()));
    assertThat(profile.getJobs(), is(nullValue()));
    verify(companyRepository, never()).findByOwner(any());
    verify(jobPostRepository, never()).findByCreatedByUserId(any());
  }

  @Test
  void getUserProfileIncludesCompanyAndCreatedJobsWhenUserIsCompanyOwner() {
    UserEntity owner = companyOwner();
    CompanyEntity company = CompanyEntity.builder()
        .id(COMPANY_ID)
        .name(COMPANY_NAME)
        .profileImageUrl(COMPANY_IMAGE_PATH)
        .owner(owner)
        .build();
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(owner));
    when(companyRepository.findByOwner(owner)).thenReturn(Optional.of(company));
    when(storageService.obtainProfilePreSignedUrl(COMPANY_IMAGE_PATH))
        .thenReturn(COMPANY_IMAGE_URL);
    when(jobPostRepository.findByCreatedByUserId(USER_ID)).thenReturn(List.of(job()));

    GetUserProfileRestResponse profile = userService.getUserProfile(USER_ID);

    assertThat(profile.getCompany().getName(), is(COMPANY_NAME));
    assertThat(profile.getCompany().getProfileImageUrl(), is(COMPANY_IMAGE_URL));
    assertThat(profile.getJobs(), hasSize(1));
    assertThat(profile.getJobs().get(0).getId(), is(JOB_ID));
    assertThat(profile.getJobs().get(0).getTitle(), is(JOB_TITLE));
    assertThat(profile.getJobs().get(0).getDescription(), is(JOB_DESCRIPTION));
    assertThat(profile.getJobs().get(0).getJobStatus(), is(JobStatus.CLOSE));
  }

  @Test
  void getUserProfileReturnsEmptyJobsWhenCompanyOwnerHasNotCreatedJobs() {
    UserEntity owner = companyOwner();
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(owner));
    when(companyRepository.findByOwner(owner)).thenReturn(Optional.empty());
    when(jobPostRepository.findByCreatedByUserId(USER_ID)).thenReturn(List.of());

    GetUserProfileRestResponse profile = userService.getUserProfile(USER_ID);

    assertThat(profile.getCompany(), is(nullValue()));
    assertThat(profile.getJobs(), is(empty()));
  }

  @Test
  void getUserProfileThrowsNotFoundWhenUserDoesNotExist() {
    when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

    NotFoundException exception = assertThrows(NotFoundException.class,
        () -> userService.getUserProfile(USER_ID));
    assertThat(exception.getMessage(), is(USER_NOT_FOUND));
  }

  @Test
  void obtainOwnedCompanyIdReturnsCompanyIdWhenCompanyOwnerOwnsACompany() {
    UserEntity owner = companyOwner();
    CompanyEntity company = CompanyEntity.builder().id(COMPANY_ID).owner(owner).build();
    when(companyRepository.findByOwner(owner)).thenReturn(Optional.of(company));

    assertThat(userService.obtainOwnedCompanyId(owner), is(COMPANY_ID));
  }

  @Test
  void obtainOwnedCompanyIdReturnsNullWhenCompanyOwnerOwnsNoCompany() {
    UserEntity owner = companyOwner();
    when(companyRepository.findByOwner(owner)).thenReturn(Optional.empty());

    assertThat(userService.obtainOwnedCompanyId(owner), is(nullValue()));
  }

  @Test
  void obtainOwnedCompanyIdReturnsNullForNonCompanyUserWithoutQueryingCompanies() {
    assertThat(userService.obtainOwnedCompanyId(candidate()), is(nullValue()));
    verify(companyRepository, never()).findByOwner(any());
  }

  private UserEntity candidate() {
    return UserEntity.builder().id(USER_ID).type(UserType.CANDIDATE).build();
  }

  private UserEntity companyOwner() {
    return UserEntity.builder().id(USER_ID).type(UserType.COMPANY).fullName(FULL_NAME).build();
  }

  private JobPostEntity job() {
    return JobPostEntity.builder()
        .id(JOB_ID)
        .title(JOB_TITLE)
        .description(JOB_DESCRIPTION)
        .jobStatus(JobStatus.CLOSE)
        .build();
  }

  private UserResumeEntity resume(String resumeJson, String storagePath) {
    return UserResumeEntity.builder()
        .id(RESUME_ID)
        .name(RESUME_NAME)
        .language(RESUME_LANGUAGE)
        .createdOn(RESUME_CREATED_ON)
        .resume(resumeJson)
        .storagePath(storagePath)
        .build();
  }

  private UserEntity existingUser() {
    return UserEntity.builder()
        .id(USER_ID)
        .type(USER_TYPE)
        .fullName(FULL_NAME)
        .profileImageUrl(PROFILE_IMAGE_URL)
        .currentPosition(CURRENT_POSITION)
        .about(ABOUT)
        .githubUrl(GITHUB_URL)
        .linkedinUrl(LINKEDIN_URL)
        .birthDate(BIRTH_DATE)
        .createdOn(LocalDate.of(2026, 6, 25))
        .build();
  }
}