package com.lynq.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynq.backend.controller.request.CreateResumeRequest;
import com.lynq.backend.controller.request.UpdateUserProfileRequest;
import com.lynq.backend.controller.response.GetUserResumeRestResponse;
import com.lynq.backend.enums.JobStatus;
import com.lynq.backend.enums.Language;
import com.lynq.backend.enums.UserType;
import com.lynq.backend.exceptions.BadRequestException;
import com.lynq.backend.exceptions.NotFoundException;
import com.lynq.backend.controller.response.GetSupportedLanguageRestResponse;
import com.lynq.backend.model.CompanyEntity;
import com.lynq.backend.model.SupportedLanguageEntity;
import com.lynq.backend.model.JobPostEntity;
import com.lynq.backend.model.UserEntity;
import com.lynq.backend.model.UserResumeEntity;
import com.lynq.backend.controller.response.DeleteResumeRestResponse;
import com.lynq.backend.model.UserSkillsEntity;
import com.lynq.backend.model.UserSimilarityTagEntity;
import com.lynq.backend.controller.response.GetUserProfileRestResponse;
import com.lynq.backend.controller.response.PagedRestResponse;
import com.lynq.backend.controller.response.UserApplicationResponse;
import com.lynq.backend.repository.CompanyRepository;
import com.lynq.backend.repository.JobPostRepository;
import com.lynq.backend.repository.UserApplicationJobRepository;
import com.lynq.backend.repository.UserRepository;
import com.lynq.backend.repository.SupportedLanguageRepository;
import com.lynq.backend.repository.UserResumeRepository;
import com.lynq.backend.repository.projection.UserApplicationProjection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.time.Month;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

  private static final String USER_ID = "550e8400-e29b-41d4-a716-446655440000";
  private static final UserType USER_TYPE = UserType.CANDIDATE;
  private static final String FULL_NAME = "Jane Doe";
  private static final String CURRENT_POSITION = "Backend Engineer";
  private static final String ABOUT = "Java developer focused on distributed systems.";
  private static final String GITHUB_URL = "https://github.com/janedoe";
  private static final String LINKEDIN_URL = "https://linkedin.com/in/janedoe";
  private static final LocalDate BIRTH_DATE = LocalDate.of(1995, Month.APRIL, 12);

  private static final String UPDATED_FULL_NAME = "Jane Q. Doe";
  private static final String UPDATED_CURRENT_POSITION = "Staff Engineer";

  private static final String FILE_NAME = "avatar.png";
  private static final String FILE_ID = "0195f2c1-3b1a-7c2d-9f31-3f6a5f2c9d41";
  private static final String PREVIOUS_FILE_ID = "0195f2c1-3b1a-7c2d-9f31-000000000000";
  private static final String PRE_SIGNED_URL =
      "https://lynq-bucket.s3.amazonaws.com/lynq/" + FILE_ID + "/" + FILE_NAME
          + "?X-Amz-Signature=abc";

  private static final String RESUME_ID = "resume-1";
  private static final String RESUME_NAME = "Jane Doe - Backend";
  private static final Language RESUME_LANGUAGE = Language.EN;
  private static final LocalDate RESUME_CREATED_ON = LocalDate.of(2026, Month.JULY, 17);
  private static final String RESUME_JSON = "{\"summary\":\"Backend engineer\",\"years\":8}";
  private static final String RESUME_FILE_ID = "0195f2c1-3b1a-7c2d-9f31-3f6a5f2c9d42";
  private static final String RESUME_PDF_URL = "https://presigned/cv.pdf";
  private static final Map<String, Object> RESUME_CONTENT = Map.of("summary", "Backend engineer");
  private static final Map<String, Object> RESUME_WITH_SKILLS = Map.of(
      "summary", "Backend engineer",
      "skills", Map.of(
          "technical", List.of("Java", "RabbitMQ"),
          "tools", List.of("Docker"),
          "soft", List.of("Leadership")));
  private static final List<String> RESUME_TAGS =
      List.of("Backend Development", "Asynchronous Messaging");

  private static final String COMPANY_ID = "company-1";
  private static final String COMPANY_NAME = "Lynq";
  private static final String COMPANY_FILE_ID = "0195f2c1-3b1a-7c2d-9f31-3f6a5f2c9d43";
  private static final String COMPANY_IMAGE_URL = "https://presigned/company-logo.png";
  private static final String JOB_ID = "job-1";
  private static final String JOB_TITLE = "Senior Backend Engineer";
  private static final String JOB_DESCRIPTION = "Build and scale the Lynq hiring platform.";

  private static final String APPLICATION_ID = "018f9c3a-2b1d-7c4e-9a6f-1e2d3c4b5a60";
  private static final String APPLICATION_ID_NEWEST = "application-newest";
  private static final String APPLICATION_ID_OLDEST = "application-oldest";
  private static final LocalDate APPLIED_ON = LocalDate.of(2026, Month.JULY, 20);
  private static final String JOB_SKILLS_CSV = "Java,Python";
  private static final Pageable DEFAULT_PAGEABLE = PageRequest.of(0, 10);

  private static final String USER_NOT_FOUND = "User '" + USER_ID + "' not found";
  private static final String ONLY_CANDIDATE_USERS_CAN_ACCESS_RESUMES =
      "Only users of type CANDIDATE can access resumes";
  private static final String ONLY_CANDIDATE_USERS_CAN_VIEW_APPLICATIONS =
      "Only users of type CANDIDATE can view their applications";

  @Mock
  private UserRepository userRepository;

  @Mock
  private UserResumeRepository userResumeRepository;

  @Mock
  private CompanyRepository companyRepository;

  @Mock
  private JobPostRepository jobPostRepository;

  @Mock
  private UserApplicationJobRepository userApplicationJobRepository;

  @Mock
  private SupportedLanguageRepository supportedLanguageRepository;

  @Mock
  private UpdateUserProfileRequest updateRequest;

  @Mock
  private FileStorageService fileStorageService;

  private final ObjectMapper objectMapper = new ObjectMapper();

  private UserService userService;

  @BeforeEach
  void setUp() {
    userService = new UserService(userRepository, userResumeRepository, companyRepository,
        jobPostRepository, userApplicationJobRepository, supportedLanguageRepository,
        fileStorageService, objectMapper);
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
    assertThat(saved.getLynqFileStorageId(), is(org.hamcrest.Matchers.nullValue()));
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
    assertThat(userCaptor.getValue().getCreatedOn(), is(LocalDate.now(ZoneOffset.UTC)));
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
  void obtainProfileImagePreSignedUrlSignsTheStoredFileId() {
    when(fileStorageService.obtainDownloadUrl(FILE_ID)).thenReturn(PRE_SIGNED_URL);

    String result = userService.obtainProfileImagePreSignedUrl(FILE_ID);

    assertThat(result, is(PRE_SIGNED_URL));
  }

  @Test
  void obtainProfileImagePreSignedUrlReturnsNullWhenImageAbsent() {
    String result = userService.obtainProfileImagePreSignedUrl(null);

    assertThat(result, is(org.hamcrest.Matchers.nullValue()));
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
    assertThat(saved.getLynqFileStorageId(), is(FILE_ID));
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
  void generateProfileImageUploadUrlPersistsFileIdAndReturnsPreSignedUrl() {
    UserEntity existing = existingUser();
    existing.setLynqFileStorageId(null);
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(existing));
    when(fileStorageService.registerUpload(FILE_NAME))
        .thenReturn(new RegisteredUpload(FILE_ID, PRE_SIGNED_URL));
    ArgumentCaptor<UserEntity> userCaptor = ArgumentCaptor.forClass(UserEntity.class);

    RegisteredUpload result = userService.generateProfileImageUploadUrl(USER_ID, FILE_NAME);

    assertThat(result.url(), is(PRE_SIGNED_URL));
    assertThat(result.fileId(), is(FILE_ID));
    verify(userRepository).save(userCaptor.capture());
    assertThat(userCaptor.getValue().getLynqFileStorageId(), is(FILE_ID));
  }

  @Test
  void generateProfileImageUploadUrlDeletesTheFileItReplaces() {
    UserEntity existing = existingUser();
    existing.setLynqFileStorageId(PREVIOUS_FILE_ID);
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(existing));
    when(fileStorageService.registerUpload(FILE_NAME))
        .thenReturn(new RegisteredUpload(FILE_ID, PRE_SIGNED_URL));

    userService.generateProfileImageUploadUrl(USER_ID, FILE_NAME);

    verify(fileStorageService).deleteFile(PREVIOUS_FILE_ID);
  }

  @Test
  void generateProfileImageUploadUrlDoesNotDeleteWhenThereWasNoPreviousImage() {
    UserEntity existing = existingUser();
    existing.setLynqFileStorageId(null);
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(existing));
    when(fileStorageService.registerUpload(FILE_NAME))
        .thenReturn(new RegisteredUpload(FILE_ID, PRE_SIGNED_URL));

    userService.generateProfileImageUploadUrl(USER_ID, FILE_NAME);

    verify(fileStorageService, never()).deleteFile(any());
  }

  @Test
  void generateProfileImageUploadUrlDoesNotDeleteWhenTheFileIdIsUnchanged() {
    UserEntity existing = existingUser();
    existing.setLynqFileStorageId(FILE_ID);
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(existing));
    when(fileStorageService.registerUpload(FILE_NAME))
        .thenReturn(new RegisteredUpload(FILE_ID, PRE_SIGNED_URL));

    userService.generateProfileImageUploadUrl(USER_ID, FILE_NAME);

    verify(fileStorageService, never()).deleteFile(any());
  }

  @Test
  void generateProfileImageUploadUrlThrowsNotFoundWhenUserDoesNotExist() {
    when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

    assertThrows(NotFoundException.class,
        () -> userService.generateProfileImageUploadUrl(USER_ID, FILE_NAME));
    verify(userRepository, never()).save(any());
  }

  @Test
  void confirmProfileImageUploadConfirmsTheFileRegisteredForTheUser() {
    UserEntity existing = existingUser();
    existing.setLynqFileStorageId(FILE_ID);
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(existing));

    userService.confirmProfileImageUpload(USER_ID, FILE_ID);

    verify(fileStorageService).confirmUpload(FILE_ID);
  }

  @Test
  void confirmProfileImageUploadThrowsBadRequestForAFileTheUserDoesNotOwn() {
    UserEntity existing = existingUser();
    existing.setLynqFileStorageId(FILE_ID);
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(existing));

    BadRequestException exception = assertThrows(BadRequestException.class,
        () -> userService.confirmProfileImageUpload(USER_ID, PREVIOUS_FILE_ID));
    assertThat(exception.getMessage(), is("File '" + PREVIOUS_FILE_ID
        + "' is not the profile image currently registered for the user"));
    verify(fileStorageService, never()).confirmUpload(any());
  }

  @Test
  void generateResumeUploadUrlReturnsPreSignedUrlForCandidate() {
    UserEntity existing = existingUser();
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(existing));
    when(fileStorageService.registerUpload(FILE_NAME))
        .thenReturn(new RegisteredUpload(FILE_ID, PRE_SIGNED_URL));

    RegisteredUpload result = userService.generateResumeUploadUrl(USER_ID, FILE_NAME);

    assertThat(result.url(), is(PRE_SIGNED_URL));
    assertThat(result.fileId(), is(FILE_ID));
  }

  @Test
  void generateResumeUploadUrlThrowsBadRequestWhenUserIsNotCandidate() {
    UserEntity company = UserEntity.builder().id(USER_ID).type(UserType.COMPANY).build();
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(company));

    BadRequestException exception = assertThrows(BadRequestException.class,
        () -> userService.generateResumeUploadUrl(USER_ID, FILE_NAME));
    assertThat(exception.getMessage(), is("Only users of type CANDIDATE can upload resumes"));
    verify(fileStorageService, never()).registerUpload(any());
  }

  @Test
  void generateResumeUploadUrlThrowsNotFoundWhenUserDoesNotExist() {
    when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

    NotFoundException exception = assertThrows(NotFoundException.class,
        () -> userService.generateResumeUploadUrl(USER_ID, FILE_NAME));
    assertThat(exception.getMessage(), is(USER_NOT_FOUND));
    verify(fileStorageService, never()).registerUpload(any());
  }

  @Test
  void confirmResumeUploadConfirmsTheGivenFileForACandidate() {
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(candidate()));

    userService.confirmResumeUpload(USER_ID, RESUME_FILE_ID);

    verify(fileStorageService).confirmUpload(RESUME_FILE_ID);
  }

  @Test
  void confirmResumeUploadThrowsBadRequestWhenUserIsNotCandidate() {
    UserEntity company = UserEntity.builder().id(USER_ID).type(UserType.COMPANY).build();
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(company));

    assertThrows(BadRequestException.class,
        () -> userService.confirmResumeUpload(USER_ID, RESUME_FILE_ID));
    verify(fileStorageService, never()).confirmUpload(any());
  }

  @Test
  void createResumePersistsTheResumePointingAtThePreviewedPdf() {
    UserEntity candidate = candidate();
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(candidate));
    when(userResumeRepository.findByUserId(USER_ID)).thenReturn(List.of());
    when(fileStorageService.obtainDownloadUrl(RESUME_FILE_ID)).thenReturn(RESUME_PDF_URL);

    GetUserResumeRestResponse result = userService.createResume(USER_ID, createRequest());

    ArgumentCaptor<UserResumeEntity> captor = ArgumentCaptor.forClass(UserResumeEntity.class);
    verify(userResumeRepository).save(captor.capture());
    UserResumeEntity saved = captor.getValue();
    assertThat(saved.getId(), is(notNullValue()));
    assertThat(saved.getName(), is(RESUME_NAME));
    assertThat(saved.getLanguage(), is(RESUME_LANGUAGE));
    assertThat(saved.getCreatedOn(), is(LocalDate.now(ZoneOffset.UTC)));
    assertThat(saved.getLynqFileStorageId(), is(RESUME_FILE_ID));
    assertThat(saved.getUser(), is(sameInstance(candidate)));
    assertThat(saved.getResume(), is("{\"summary\":\"Backend engineer\"}"));

    assertThat(result.getName(), is(RESUME_NAME));
    assertThat(result.getPdfUrl(), is(RESUME_PDF_URL));
    @SuppressWarnings("unchecked")
    Map<String, Object> resumeJson = (Map<String, Object>) result.getResume();
    assertThat(resumeJson.get("summary"), is("Backend engineer"));
  }

  @Test
  void createResumeFeedsTheCandidateSkillsAndTagsTheLynqScoreMatchesOn() {
    UserEntity candidate = candidate();
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(candidate));
    when(userResumeRepository.findByUserId(USER_ID)).thenReturn(List.of());
    when(fileStorageService.obtainDownloadUrl(RESUME_FILE_ID)).thenReturn(RESUME_PDF_URL);

    userService.createResume(USER_ID, requestWithSkills());

    assertThat(candidate.getSkills().stream().map(UserSkillsEntity::getSkill).toList(),
        contains("Java", "RabbitMQ", "Docker"));
    assertThat(candidate.getSimilarityTags().stream().map(UserSimilarityTagEntity::getSimilarityTag).toList(),
        contains("Backend Development", "Asynchronous Messaging"));
    verify(userRepository).save(candidate);
  }

  @Test
  void createResumeAddsToTheSkillsTheCandidateAlreadyHasInsteadOfReplacingThem() {
    UserEntity candidate = candidate();
    candidate.getSkills().add(UserSkillsEntity.builder()
        .id("existing-skill").user(candidate).skill("java").build());
    candidate.getSimilarityTags().add(UserSimilarityTagEntity.builder()
        .id("existing-tag").user(candidate).similarityTag("Backend Development").build());
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(candidate));
    when(userResumeRepository.findByUserId(USER_ID)).thenReturn(List.of());
    when(fileStorageService.obtainDownloadUrl(RESUME_FILE_ID)).thenReturn(RESUME_PDF_URL);

    userService.createResume(USER_ID, requestWithSkills());

    assertThat(candidate.getSkills().stream().map(UserSkillsEntity::getSkill).toList(),
        contains("java", "RabbitMQ", "Docker"));
    assertThat(candidate.getSimilarityTags().stream().map(UserSimilarityTagEntity::getSimilarityTag).toList(),
        contains("Backend Development", "Asynchronous Messaging"));
  }

  @Test
  void createResumeLeavesTheCandidateSkillsAloneWhenTheResumeCarriesNone() {
    UserEntity candidate = candidate();
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(candidate));
    when(userResumeRepository.findByUserId(USER_ID)).thenReturn(List.of());
    when(fileStorageService.obtainDownloadUrl(RESUME_FILE_ID)).thenReturn(RESUME_PDF_URL);

    userService.createResume(USER_ID, createRequest());

    assertThat(candidate.getSkills(), is(empty()));
    assertThat(candidate.getSimilarityTags(), is(empty()));
  }

  @Test
  void createResumeThrowsBadRequestWhenThePdfAlreadyBacksAnotherResume() {
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(candidate()));
    when(userResumeRepository.findByUserId(USER_ID))
        .thenReturn(List.of(resume(RESUME_JSON, RESUME_FILE_ID)));

    BadRequestException exception = assertThrows(BadRequestException.class,
        () -> userService.createResume(USER_ID, createRequest()));
    assertThat(exception.getMessage(),
        is("File '" + RESUME_FILE_ID + "' already backs one of the user's resumes"));
    verify(userResumeRepository, never()).save(any());
  }

  @Test
  void createResumeThrowsBadRequestWhenUserIsNotCandidate() {
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(companyOwner()));

    assertThrows(BadRequestException.class, () -> userService.createResume(USER_ID, createRequest()));
    verify(userResumeRepository, never()).save(any());
  }

  @Test
  void deleteResumeRemovesItAndHandsBackThePdfForTheGatewayToDrop() {
    UserResumeEntity resume = resume(RESUME_JSON, RESUME_FILE_ID);
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(candidate()));
    when(userResumeRepository.findByUserId(USER_ID)).thenReturn(List.of(resume));

    DeleteResumeRestResponse deleted = userService.deleteResume(USER_ID, resume.getId());

    verify(userResumeRepository).delete(resume);
    assertThat(deleted.getId(), is(resume.getId()));
    assertThat(deleted.getFileId(), is(RESUME_FILE_ID));
  }

  @Test
  void deleteResumeKeepsTheCandidateSkills() {
    UserEntity candidate = candidate();
    candidate.getSkills().add(UserSkillsEntity.builder()
        .id("skill-1").user(candidate).skill("Java").build());
    UserResumeEntity resume = resume(RESUME_JSON, RESUME_FILE_ID);
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(candidate));
    when(userResumeRepository.findByUserId(USER_ID)).thenReturn(List.of(resume));

    userService.deleteResume(USER_ID, resume.getId());

    assertThat(candidate.getSkills().stream().map(UserSkillsEntity::getSkill).toList(),
        contains("Java"));
  }

  @Test
  void deleteResumeThrowsNotFoundWhenItBelongsToAnotherCandidate() {
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(candidate()));
    when(userResumeRepository.findByUserId(USER_ID)).thenReturn(List.of());

    NotFoundException exception = assertThrows(NotFoundException.class,
        () -> userService.deleteResume(USER_ID, "someone-elses-resume"));

    assertThat(exception.getMessage(), is("Resume 'someone-elses-resume' not found"));
    verify(userResumeRepository, never()).delete(any());
  }

  @Test
  void deleteResumeThrowsBadRequestWhenUserIsNotCandidate() {
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(companyOwner()));

    assertThrows(BadRequestException.class,
        () -> userService.deleteResume(USER_ID, "any-resume"));
    verify(userResumeRepository, never()).delete(any());
  }

  @Test
  void getUserResumesMapsEntitiesWithParsedJsonAndPresignedPdfUrl() {
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(candidate()));
    when(userResumeRepository.findByUserId(USER_ID))
        .thenReturn(List.of(resume(RESUME_JSON, RESUME_FILE_ID)));
    when(fileStorageService.obtainDownloadUrls(List.of(RESUME_FILE_ID)))
        .thenReturn(Map.of(RESUME_FILE_ID, RESUME_PDF_URL));

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
  void getUserResumesLeavesPdfUrlNullWhenTheResumeHasNoFile() {
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(candidate()));
    when(userResumeRepository.findByUserId(USER_ID))
        .thenReturn(List.of(resume(RESUME_JSON, null)));
    when(fileStorageService.obtainDownloadUrls(anyList())).thenReturn(Map.of());

    GetUserResumeRestResponse response = userService.getUserResumes(USER_ID).get(0);

    assertThat(response.getPdfUrl(), is(nullValue()));
  }

  @Test
  void getUserResumesLeavesResumeNullWhenJsonIsBlank() {
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(candidate()));
    when(userResumeRepository.findByUserId(USER_ID))
        .thenReturn(List.of(resume(null, RESUME_FILE_ID)));
    when(fileStorageService.obtainDownloadUrls(List.of(RESUME_FILE_ID)))
        .thenReturn(Map.of(RESUME_FILE_ID, RESUME_PDF_URL));

    GetUserResumeRestResponse response = userService.getUserResumes(USER_ID).get(0);

    assertThat(response.getResume(), is(nullValue()));
  }

  @Test
  void getSupportedResumeLanguagesReturnsEveryRowAsCodeAndName() {
    when(supportedLanguageRepository.findAll()).thenReturn(List.of(
        SupportedLanguageEntity.builder().code("EN").name("English").build(),
        SupportedLanguageEntity.builder().code("ES").name("Español").build()));

    List<GetSupportedLanguageRestResponse> languages = userService.getSupportedResumeLanguages();

    assertThat(languages, hasSize(2));
    assertThat(languages.get(0).getCode(), is("EN"));
    assertThat(languages.get(0).getName(), is("English"));
    assertThat(languages.get(1).getCode(), is("ES"));
    assertThat(languages.get(1).getName(), is("Español"));
  }

  @Test
  void getSupportedResumeLanguagesReturnsEmptyWhenTableIsEmpty() {
    when(supportedLanguageRepository.findAll()).thenReturn(List.of());

    assertThat(userService.getSupportedResumeLanguages(), is(empty()));
  }

  @Test
  void getUserResumesReturnsEmptyWhenCandidateHasNoResumes() {
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(candidate()));
    when(userResumeRepository.findByUserId(USER_ID)).thenReturn(List.of());
    when(fileStorageService.obtainDownloadUrls(List.of())).thenReturn(Map.of());

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
    existing.setLynqFileStorageId(FILE_ID);
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(existing));
    when(fileStorageService.obtainDownloadUrl(FILE_ID)).thenReturn(PRE_SIGNED_URL);

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
    owner.setLynqFileStorageId(FILE_ID);
    CompanyEntity company = CompanyEntity.builder()
        .id(COMPANY_ID)
        .name(COMPANY_NAME)
        .lynqFileStorageId(COMPANY_FILE_ID)
        .owner(owner)
        .build();
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(owner));
    when(companyRepository.findByOwner(owner)).thenReturn(Optional.of(company));
    when(fileStorageService.obtainDownloadUrl(COMPANY_FILE_ID)).thenReturn(COMPANY_IMAGE_URL);
    when(fileStorageService.obtainDownloadUrl(FILE_ID)).thenReturn(PRE_SIGNED_URL);
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
    CompanyEntity company = CompanyEntity.builder().id(COMPANY_ID).build();
    when(companyRepository.findByOwnerId(USER_ID)).thenReturn(Optional.of(company));

    assertThat(userService.obtainOwnedCompanyId(USER_ID, UserType.COMPANY), is(COMPANY_ID));
  }

  @Test
  void obtainOwnedCompanyIdReturnsNullWhenCompanyOwnerOwnsNoCompany() {
    when(companyRepository.findByOwnerId(USER_ID)).thenReturn(Optional.empty());

    assertThat(userService.obtainOwnedCompanyId(USER_ID, UserType.COMPANY), is(nullValue()));
  }

  @Test
  void obtainOwnedCompanyIdReturnsNullForNonCompanyUserWithoutQueryingCompanies() {
    assertThat(userService.obtainOwnedCompanyId(USER_ID, UserType.CANDIDATE), is(nullValue()));
    verify(companyRepository, never()).findByOwnerId(any());
  }

  @Test
  void getUserApplicationsMapsProjectionFieldsAndSignsTheCompanyLogo() {
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(candidateWithSkills()));
    when(fileStorageService.obtainDownloadUrls(List.of(COMPANY_FILE_ID)))
        .thenReturn(Map.of(COMPANY_FILE_ID, COMPANY_IMAGE_URL));
    when(userApplicationJobRepository.findApplicationsByUserId(USER_ID, DEFAULT_PAGEABLE))
        .thenReturn(new PageImpl<>(List.of(applicationProjection(APPLICATION_ID, COMPANY_FILE_ID)),
            DEFAULT_PAGEABLE, 1));

    PagedRestResponse<UserApplicationResponse> result =
        userService.getUserApplications(USER_ID, DEFAULT_PAGEABLE);

    assertThat(result.getContent(), hasSize(1));
    UserApplicationResponse application = result.getContent().get(0);
    assertThat(application.getId(), is(APPLICATION_ID));
    assertThat(application.getJobId(), is(JOB_ID));
    assertThat(application.getJobTitle(), is(JOB_TITLE));
    assertThat(application.getJobDescription(), is(JOB_DESCRIPTION));
    assertThat(application.getCompanyId(), is(COMPANY_ID));
    assertThat(application.getCompanyName(), is(COMPANY_NAME));
    assertThat(application.getCompanyProfileImage(), is(COMPANY_IMAGE_URL));
    assertThat(application.getAppliedOn(), is(APPLIED_ON));
  }

  @Test
  void getUserApplicationsScoresLynqAsPercentageOfMatchingJobSkills() {
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(candidateWithSkills()));
    when(userApplicationJobRepository.findApplicationsByUserId(USER_ID, DEFAULT_PAGEABLE))
        .thenReturn(new PageImpl<>(List.of(applicationProjection(APPLICATION_ID, null)),
            DEFAULT_PAGEABLE, 1));
    when(fileStorageService.obtainDownloadUrls(anyList())).thenReturn(Map.of());

    UserApplicationResponse application =
        userService.getUserApplications(USER_ID, DEFAULT_PAGEABLE).getContent().get(0);

    assertThat(application.getLynqScore(), is(50));
  }

  @Test
  void getUserApplicationsScoresLynqOnTheCapabilityTagsWhenTheyMatchBetterThanTheSkills() {
    UserEntity candidate = candidate();
    candidate.setSkills(List.of(UserSkillsEntity.builder().skill("Java").build()));
    candidate.setSimilarityTags(List.of(
        UserSimilarityTagEntity.builder().similarityTag("Backend Development").build(),
        UserSimilarityTagEntity.builder().similarityTag("Asynchronous Messaging").build()));
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(candidate));
    when(userApplicationJobRepository.findApplicationsByUserId(USER_ID, DEFAULT_PAGEABLE))
        .thenReturn(new PageImpl<>(List.of(new UserApplicationProjection(
            APPLICATION_ID, JOB_ID, JOB_TITLE, JOB_DESCRIPTION, COMPANY_ID, COMPANY_NAME, null,
            APPLIED_ON, "Java,Kafka,Terraform",
            "Backend Development,Asynchronous Messaging")), DEFAULT_PAGEABLE, 1));
    when(fileStorageService.obtainDownloadUrls(anyList())).thenReturn(Map.of());

    UserApplicationResponse application =
        userService.getUserApplications(USER_ID, DEFAULT_PAGEABLE).getContent().get(0);

    assertThat(application.getLynqScore(), is(100));
  }

  @Test
  void getUserApplicationsKeepsTheSkillScoreWhenTheJobCarriesNoTags() {
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(candidateWithSkills()));
    when(userApplicationJobRepository.findApplicationsByUserId(USER_ID, DEFAULT_PAGEABLE))
        .thenReturn(new PageImpl<>(List.of(applicationProjection(APPLICATION_ID, null)),
            DEFAULT_PAGEABLE, 1));
    when(fileStorageService.obtainDownloadUrls(anyList())).thenReturn(Map.of());

    UserApplicationResponse application =
        userService.getUserApplications(USER_ID, DEFAULT_PAGEABLE).getContent().get(0);

    assertThat(application.getLynqScore(), is(50));
  }

  @Test
  void getUserApplicationsScoresLynqZeroWhenCandidateHasNoSkills() {
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(candidate()));
    when(userApplicationJobRepository.findApplicationsByUserId(USER_ID, DEFAULT_PAGEABLE))
        .thenReturn(new PageImpl<>(List.of(applicationProjection(APPLICATION_ID, null)),
            DEFAULT_PAGEABLE, 1));
    when(fileStorageService.obtainDownloadUrls(anyList())).thenReturn(Map.of());

    UserApplicationResponse application =
        userService.getUserApplications(USER_ID, DEFAULT_PAGEABLE).getContent().get(0);

    assertThat(application.getLynqScore(), is(0));
  }

  @Test
  void getUserApplicationsLeavesCompanyImageNullWhenTheCompanyHasNoLogo() {
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(candidateWithSkills()));
    when(userApplicationJobRepository.findApplicationsByUserId(USER_ID, DEFAULT_PAGEABLE))
        .thenReturn(new PageImpl<>(List.of(applicationProjection(APPLICATION_ID, null)),
            DEFAULT_PAGEABLE, 1));
    when(fileStorageService.obtainDownloadUrls(anyList())).thenReturn(Map.of());

    UserApplicationResponse application =
        userService.getUserApplications(USER_ID, DEFAULT_PAGEABLE).getContent().get(0);

    assertThat(application.getCompanyProfileImage(), is(nullValue()));
  }

  @Test
  void getUserApplicationsMapsPaginationMetadataAndPreservesOrder() {
    Pageable pageable = PageRequest.of(1, 2);
    Page<UserApplicationProjection> page = new PageImpl<>(
        List.of(applicationProjection(APPLICATION_ID_NEWEST, null),
            applicationProjection(APPLICATION_ID_OLDEST, null)), pageable, 6);
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(candidateWithSkills()));
    when(userApplicationJobRepository.findApplicationsByUserId(USER_ID, pageable)).thenReturn(page);
    when(fileStorageService.obtainDownloadUrls(anyList())).thenReturn(Map.of());

    PagedRestResponse<UserApplicationResponse> result =
        userService.getUserApplications(USER_ID, pageable);

    assertThat(result.getPage(), is(1));
    assertThat(result.getSize(), is(2));
    assertThat(result.getTotalElements(), is(6L));
    assertThat(result.getTotalPages(), is(3));
    assertThat(result.isHasNext(), is(true));
    assertThat(result.isHasPrevious(), is(true));
    assertThat(result.getContent().stream().map(UserApplicationResponse::getId).toList(),
        contains(APPLICATION_ID_NEWEST, APPLICATION_ID_OLDEST));
  }

  @Test
  void getUserApplicationsReturnsEmptyContentWhenCandidateHasNoApplications() {
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(candidateWithSkills()));
    when(userApplicationJobRepository.findApplicationsByUserId(USER_ID, DEFAULT_PAGEABLE))
        .thenReturn(new PageImpl<>(List.of(), DEFAULT_PAGEABLE, 0));
    when(fileStorageService.obtainDownloadUrls(anyList())).thenReturn(Map.of());

    PagedRestResponse<UserApplicationResponse> result =
        userService.getUserApplications(USER_ID, DEFAULT_PAGEABLE);

    assertThat(result.getContent(), is(empty()));
    assertThat(result.getTotalElements(), is(0L));
  }

  @Test
  void getUserApplicationsThrowsBadRequestWhenUserIsNotCandidate() {
    UserEntity company = UserEntity.builder().id(USER_ID).type(UserType.COMPANY).build();
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(company));

    BadRequestException exception = assertThrows(BadRequestException.class,
        () -> userService.getUserApplications(USER_ID, DEFAULT_PAGEABLE));
    assertThat(exception.getMessage(), is(ONLY_CANDIDATE_USERS_CAN_VIEW_APPLICATIONS));
    verify(userApplicationJobRepository, never()).findApplicationsByUserId(any(), any());
  }

  @Test
  void getUserApplicationsThrowsNotFoundWhenUserDoesNotExist() {
    when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

    NotFoundException exception = assertThrows(NotFoundException.class,
        () -> userService.getUserApplications(USER_ID, DEFAULT_PAGEABLE));
    assertThat(exception.getMessage(), is(USER_NOT_FOUND));
    verify(userApplicationJobRepository, never()).findApplicationsByUserId(any(), any());
  }

  private UserApplicationProjection applicationProjection(String id, String companyImagePath) {
    return new UserApplicationProjection(id, JOB_ID, JOB_TITLE, JOB_DESCRIPTION, COMPANY_ID,
        COMPANY_NAME, companyImagePath, APPLIED_ON, JOB_SKILLS_CSV, null);
  }

  private UserEntity candidateWithSkills() {
    UserEntity candidate = candidate();
    candidate.setSkills(List.of(
        UserSkillsEntity.builder().skill("java").build(),
        UserSkillsEntity.builder().skill("kotlin").build()));
    return candidate;
  }

  private CreateResumeRequest requestWithSkills() {
    CreateResumeRequest request = createRequest();
    request.setResume(RESUME_WITH_SKILLS);
    request.setSimilarityTags(RESUME_TAGS);
    return request;
  }

  private CreateResumeRequest createRequest() {
    CreateResumeRequest request = new CreateResumeRequest();
    request.setName(RESUME_NAME);
    request.setLanguage(RESUME_LANGUAGE);
    request.setResume(RESUME_CONTENT);
    request.setFileId(RESUME_FILE_ID);
    return request;
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

  private UserResumeEntity resume(String resumeJson, String lynqFileStorageId) {
    return UserResumeEntity.builder()
        .id(RESUME_ID)
        .name(RESUME_NAME)
        .language(RESUME_LANGUAGE)
        .createdOn(RESUME_CREATED_ON)
        .resume(resumeJson)
        .lynqFileStorageId(lynqFileStorageId)
        .build();
  }

  private UserEntity existingUser() {
    return UserEntity.builder()
        .id(USER_ID)
        .type(USER_TYPE)
        .fullName(FULL_NAME)
        .lynqFileStorageId(FILE_ID)
        .currentPosition(CURRENT_POSITION)
        .about(ABOUT)
        .githubUrl(GITHUB_URL)
        .linkedinUrl(LINKEDIN_URL)
        .birthDate(BIRTH_DATE)
        .createdOn(LocalDate.of(2026, Month.JUNE, 25))
        .build();
  }
}