package com.lynq.backend.service;

import com.lynq.backend.controller.request.CreateUserWithCompanyRequest;
import com.lynq.backend.enums.UserType;
import com.lynq.backend.controller.response.GetCompanyDetailRestResponse;
import com.lynq.backend.enums.JobStatus;
import com.lynq.backend.exceptions.BadRequestException;
import com.lynq.backend.exceptions.NotFoundException;
import com.lynq.backend.model.CompanyEntity;
import com.lynq.backend.model.JobPostEntity;
import com.lynq.backend.model.UserEntity;
import com.lynq.backend.repository.CompanyRepository;
import com.lynq.backend.repository.JobPostRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompanyServiceTest {

  private static final String USER_ID = "550e8400-e29b-41d4-a716-446655440000";
  private static final String CURRENT_POSITION = "Founder";
  private static final String USER_ABOUT = "Building the Lynq hiring platform.";
  private static final String LINKEDIN_URL = "https://linkedin.com/in/janedoe";
  private static final LocalDate BIRTH_DATE = LocalDate.of(1995, 4, 12);
  private static final String COMPANY_NAME = "Lynq Technologies";
  private static final String COMPANY_ABOUT = "We build talent matching platforms.";
  private static final Integer COMPANY_SIZE = 250;
  private static final String COMPANY_PROFILE_IMAGE_URL = "https://cdn.lynq.com/logos/lynq.png";
  private static final String NO_GITHUB_URL = null;
  private static final String FULL_NAME = "Jane Doe";
  private static final String FILE_NAME = "logo.png";
  private static final String S3_PATH = "lynq/companies/company-1/profile/logo.png";
  private static final String PREVIOUS_S3_PATH = "lynq/companies/company-1/profile/old.png";
  private static final String PRE_SIGNED_URL = "https://lynq-bucket.s3.amazonaws.com/logo.png?sig=abc";
  private static final String COMPANY_ID = "company-1";
  private static final String COMPANY_IMAGE_PATH = "lynq/companies/" + COMPANY_ID + "/profile/logo.png";
  private static final String COMPANY_IMAGE_URL = "https://presigned/company-logo.png";
  private static final LocalDate COMPANY_CREATED_ON = LocalDate.of(2026, 6, 25);
  private static final String JOB_ID = "job-1";
  private static final String JOB_TITLE = "Senior Backend Engineer";
  private static final String JOB_DESCRIPTION = "Build and scale the Lynq hiring platform.";
  private static final String COMPANY_NOT_FOUND = "Company '" + COMPANY_ID + "' not found";

  @Mock
  private UserService userService;

  @Mock
  private CompanyRepository companyRepository;

  @Mock
  private JobPostRepository jobPostRepository;

  @Mock
  private CreateUserWithCompanyRequest request;

  @Mock
  private StorageService storageService;

  private CompanyService companyService;

  @BeforeEach
  void setUp() {
    companyService = new CompanyService(userService, companyRepository, jobPostRepository,
        storageService);
  }

  @Test
  void createUserWithCompanyCreatesOwnerAsCompanyUserFromRequest() {
    stubRequestFields();

    companyService.createUserWithCompany(USER_ID, request);

    verify(userService).saveNewUser(USER_ID, UserType.COMPANY, FULL_NAME,
        CURRENT_POSITION, USER_ABOUT, NO_GITHUB_URL, LINKEDIN_URL, BIRTH_DATE);
  }

  @Test
  void createUserWithCompanyPersistsCompanyBuiltFromRequestAndOwner() {
    stubRequestFields();
    UserEntity owner = UserEntity.builder().id(USER_ID).build();
    when(userService.saveNewUser(USER_ID, UserType.COMPANY, FULL_NAME,
        CURRENT_POSITION, USER_ABOUT, NO_GITHUB_URL, LINKEDIN_URL, BIRTH_DATE)).thenReturn(owner);
    when(companyRepository.save(any(CompanyEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    ArgumentCaptor<CompanyEntity> companyCaptor = ArgumentCaptor.forClass(CompanyEntity.class);

    companyService.createUserWithCompany(USER_ID, request);

    verify(companyRepository).save(companyCaptor.capture());
    CompanyEntity saved = companyCaptor.getValue();
    assertThat(saved.getName(), is(COMPANY_NAME));
    assertThat(saved.getAbout(), is(COMPANY_ABOUT));
    assertThat(saved.getSize(), is(COMPANY_SIZE));
    assertThat(saved.getProfileImageUrl(), is(COMPANY_PROFILE_IMAGE_URL));
    assertThat(saved.getCreatedOn(), is(LocalDate.now()));
    assertThat(saved.getOwner(), is(sameInstance(owner)));
    assertThat(saved.getId(), is(notNullValue()));
  }

  @Test
  void createUserWithCompanyGeneratesUuidCompanyId() {
    stubRequestFields();
    when(companyRepository.save(any(CompanyEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    ArgumentCaptor<CompanyEntity> companyCaptor = ArgumentCaptor.forClass(CompanyEntity.class);

    companyService.createUserWithCompany(USER_ID, request);

    verify(companyRepository).save(companyCaptor.capture());
    assertThat(UUID.fromString(companyCaptor.getValue().getId()), is(notNullValue()));
  }

  @Test
  void createUserWithCompanyReturnsEntityProducedByRepository() {
    stubRequestFields();
    CompanyEntity persisted = CompanyEntity.builder().id(COMPANY_NAME).build();
    when(companyRepository.save(any(CompanyEntity.class))).thenReturn(persisted);

    CompanyEntity result = companyService.createUserWithCompany(USER_ID, request);

    assertThat(result, is(sameInstance(persisted)));
  }

  @Test
  void createUserWithCompanyThrowsBadRequestWhenCompanyNameAlreadyExists() {
    when(request.getCompanyName()).thenReturn(COMPANY_NAME);
    when(companyRepository.existsByName(COMPANY_NAME)).thenReturn(true);

    BadRequestException exception = assertThrows(BadRequestException.class,
        () -> companyService.createUserWithCompany(USER_ID, request));
    assertThat(exception.getMessage(), containsString(COMPANY_NAME));
  }

  @Test
  void createUserWithCompanyDoesNotCreateOwnerOrPersistWhenCompanyNameAlreadyExists() {
    when(request.getCompanyName()).thenReturn(COMPANY_NAME);
    when(companyRepository.existsByName(COMPANY_NAME)).thenReturn(true);

    assertThrows(BadRequestException.class,
        () -> companyService.createUserWithCompany(USER_ID, request));

    verify(userService, never()).saveNewUser(any(), any(), any(), any(), any(), any(), any(), any());
    verify(companyRepository, never()).save(any());
  }

  @Test
  void generateCompanyImageUploadUrlPersistsS3PathAndReturnsPreSignedUrl() {
    UserEntity owner = UserEntity.builder().id(USER_ID).build();
    CompanyEntity company = companyOwnedBy(owner);
    when(userService.getUser(USER_ID)).thenReturn(owner);
    when(companyRepository.findByOwner(owner)).thenReturn(Optional.of(company));
    when(storageService.createCompanyProfilePreSignedUrl(company, FILE_NAME))
        .thenReturn(new PreSignedUploadUrl(S3_PATH, PRE_SIGNED_URL));
    ArgumentCaptor<CompanyEntity> companyCaptor = ArgumentCaptor.forClass(CompanyEntity.class);

    String result = companyService.generateCompanyImageUploadUrl(USER_ID, FILE_NAME);

    assertThat(result, is(PRE_SIGNED_URL));
    verify(companyRepository).save(companyCaptor.capture());
    assertThat(companyCaptor.getValue().getProfileImageUrl(), is(S3_PATH));
  }

  @Test
  void generateCompanyImageUploadUrlDeletesPreviousObjectWhenPathChanges() {
    UserEntity owner = UserEntity.builder().id(USER_ID).build();
    CompanyEntity company = companyOwnedBy(owner);
    company.setProfileImageUrl(PREVIOUS_S3_PATH);
    when(userService.getUser(USER_ID)).thenReturn(owner);
    when(companyRepository.findByOwner(owner)).thenReturn(Optional.of(company));
    when(storageService.createCompanyProfilePreSignedUrl(company, FILE_NAME))
        .thenReturn(new PreSignedUploadUrl(S3_PATH, PRE_SIGNED_URL));

    companyService.generateCompanyImageUploadUrl(USER_ID, FILE_NAME);

    verify(storageService).deleteObject(PREVIOUS_S3_PATH);
  }

  @Test
  void generateCompanyImageUploadUrlDoesNotDeleteWhenNoPreviousObjectExists() {
    UserEntity owner = UserEntity.builder().id(USER_ID).build();
    CompanyEntity company = companyOwnedBy(owner);
    company.setProfileImageUrl(null);
    when(userService.getUser(USER_ID)).thenReturn(owner);
    when(companyRepository.findByOwner(owner)).thenReturn(Optional.of(company));
    when(storageService.createCompanyProfilePreSignedUrl(company, FILE_NAME))
        .thenReturn(new PreSignedUploadUrl(S3_PATH, PRE_SIGNED_URL));

    companyService.generateCompanyImageUploadUrl(USER_ID, FILE_NAME);

    verify(storageService, never()).deleteObject(any());
  }

  @Test
  void generateCompanyImageUploadUrlDoesNotDeleteWhenPathIsUnchanged() {
    UserEntity owner = UserEntity.builder().id(USER_ID).build();
    CompanyEntity company = companyOwnedBy(owner);
    company.setProfileImageUrl(S3_PATH);
    when(userService.getUser(USER_ID)).thenReturn(owner);
    when(companyRepository.findByOwner(owner)).thenReturn(Optional.of(company));
    when(storageService.createCompanyProfilePreSignedUrl(company, FILE_NAME))
        .thenReturn(new PreSignedUploadUrl(S3_PATH, PRE_SIGNED_URL));

    companyService.generateCompanyImageUploadUrl(USER_ID, FILE_NAME);

    verify(storageService, never()).deleteObject(any());
  }

  @Test
  void generateCompanyImageUploadUrlThrowsNotFoundWhenUserOwnsNoCompany() {
    UserEntity owner = UserEntity.builder().id(USER_ID).build();
    when(userService.getUser(USER_ID)).thenReturn(owner);
    when(companyRepository.findByOwner(owner)).thenReturn(Optional.empty());

    assertThrows(NotFoundException.class,
        () -> companyService.generateCompanyImageUploadUrl(USER_ID, FILE_NAME));
    verify(companyRepository, never()).save(any());
  }

  @Test
  void getCompanyDetailMapsCompanyFieldsPresignedImageAndAllJobsRegardlessOfStatus() {
    CompanyEntity company = CompanyEntity.builder()
        .id(COMPANY_ID)
        .name(COMPANY_NAME)
        .about(COMPANY_ABOUT)
        .size(COMPANY_SIZE)
        .profileImageUrl(COMPANY_IMAGE_PATH)
        .createdOn(COMPANY_CREATED_ON)
        .build();
    when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(company));
    when(storageService.obtainProfilePreSignedUrl(COMPANY_IMAGE_PATH))
        .thenReturn(COMPANY_IMAGE_URL);
    when(jobPostRepository.findByCompanyId(COMPANY_ID)).thenReturn(java.util.List.of(job()));

    GetCompanyDetailRestResponse detail = companyService.getCompanyDetail(COMPANY_ID);

    assertThat(detail.getId(), is(COMPANY_ID));
    assertThat(detail.getName(), is(COMPANY_NAME));
    assertThat(detail.getAbout(), is(COMPANY_ABOUT));
    assertThat(detail.getSize(), is(COMPANY_SIZE));
    assertThat(detail.getProfileImageUrl(), is(COMPANY_IMAGE_URL));
    assertThat(detail.getCreatedOn(), is(COMPANY_CREATED_ON));
    assertThat(detail.getJobs(), org.hamcrest.Matchers.hasSize(1));
    assertThat(detail.getJobs().get(0).getId(), is(JOB_ID));
    assertThat(detail.getJobs().get(0).getTitle(), is(JOB_TITLE));
    assertThat(detail.getJobs().get(0).getDescription(), is(JOB_DESCRIPTION));
    assertThat(detail.getJobs().get(0).getJobStatus(), is(JobStatus.CLOSE));
  }

  @Test
  void getCompanyDetailLeavesProfileImageNullWhenPathIsBlank() {
    CompanyEntity company = CompanyEntity.builder()
        .id(COMPANY_ID)
        .name(COMPANY_NAME)
        .profileImageUrl(null)
        .build();
    when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.of(company));
    when(jobPostRepository.findByCompanyId(COMPANY_ID)).thenReturn(java.util.List.of());

    GetCompanyDetailRestResponse detail = companyService.getCompanyDetail(COMPANY_ID);

    assertThat(detail.getProfileImageUrl(), is(org.hamcrest.Matchers.nullValue()));
    assertThat(detail.getJobs(), is(org.hamcrest.Matchers.empty()));
    verify(storageService, never()).obtainProfilePreSignedUrl(any());
  }

  @Test
  void getCompanyDetailThrowsNotFoundWhenCompanyDoesNotExist() {
    when(companyRepository.findById(COMPANY_ID)).thenReturn(Optional.empty());

    NotFoundException exception = assertThrows(NotFoundException.class,
        () -> companyService.getCompanyDetail(COMPANY_ID));
    assertThat(exception.getMessage(), is(COMPANY_NOT_FOUND));
    verify(jobPostRepository, never()).findByCompanyId(any());
  }

  private JobPostEntity job() {
    return JobPostEntity.builder()
        .id(JOB_ID)
        .title(JOB_TITLE)
        .description(JOB_DESCRIPTION)
        .jobStatus(JobStatus.CLOSE)
        .build();
  }

  private CompanyEntity companyOwnedBy(UserEntity owner) {
    return CompanyEntity.builder()
        .id("company-1")
        .name(COMPANY_NAME)
        .about(COMPANY_ABOUT)
        .size(COMPANY_SIZE)
        .createdOn(LocalDate.of(2026, 6, 25))
        .owner(owner)
        .build();
  }

  private void stubRequestFields() {
    when(request.getCompanyName()).thenReturn(COMPANY_NAME);
    when(request.getFullName()).thenReturn(FULL_NAME);
    when(request.getCurrentPosition()).thenReturn(CURRENT_POSITION);
    when(request.getUserAbout()).thenReturn(USER_ABOUT);
    when(request.getLinkedinUrl()).thenReturn(LINKEDIN_URL);
    when(request.getBirthDate()).thenReturn(BIRTH_DATE);
    when(request.getCompanyAbout()).thenReturn(COMPANY_ABOUT);
    when(request.getCompanySize()).thenReturn(COMPANY_SIZE);
    when(request.getCompanyProfileImageUrl()).thenReturn(COMPANY_PROFILE_IMAGE_URL);
  }
}