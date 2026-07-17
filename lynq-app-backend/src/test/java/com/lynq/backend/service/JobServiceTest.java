package com.lynq.backend.service;

import com.lynq.backend.controller.response.GetJobRestResponse;
import com.lynq.backend.controller.response.JobCandidateResponse;
import com.lynq.backend.controller.response.PagedRestResponse;
import com.lynq.backend.enums.JobPostSource;
import com.lynq.backend.enums.UserType;
import com.lynq.backend.enums.WorkType;
import com.lynq.backend.exceptions.AlreadyAppliedToJobException;
import com.lynq.backend.exceptions.BadRequestException;
import com.lynq.backend.exceptions.NotFoundException;
import com.lynq.backend.model.CompanyEntity;
import com.lynq.backend.model.JobPostEntity;
import com.lynq.backend.model.JobPostSkillEntity;
import com.lynq.backend.model.UserApplicationJobEntity;
import com.lynq.backend.model.UserEntity;
import com.lynq.backend.model.UserSkillsEntity;
import com.lynq.backend.repository.CompanyRepository;
import com.lynq.backend.repository.JobPostRepository;
import com.lynq.backend.repository.UserApplicationJobRepository;
import com.lynq.backend.repository.UserRepository;
import com.lynq.backend.repository.projection.JobCandidateProjection;
import com.lynq.backend.repository.projection.JobWithDetailsProjection;
import com.lynq.backend.security.LynqUserPrincipal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobServiceTest {

  private static final String USER_ID = "550e8400-e29b-41d4-a716-446655440000";
  private static final String USERNAME = "janedoe";
  private static final String EMAIL = "jane@lynq.com";
  private static final String TITLE = "Senior Backend Engineer";
  private static final String DESCRIPTION = "Build and scale the Lynq hiring platform.";
  private static final WorkType WORK_TYPE = WorkType.REMOTE;
  private static final Integer SALARY_RANGE_DOWN = 80000;
  private static final Integer SALARY_RANGE_TOP = 120000;
  private static final JobPostSource JOB_POST_TYPE = JobPostSource.LYNQ;

  private static final String SKILL_JAVA = "Java";
  private static final String SKILL_SPRING = "Spring";
  private static final String SKILL_POSTGRES = "PostgreSQL";
  private static final List<String> NO_SKILLS = null;
  private static final List<String> SKILLS = List.of(SKILL_JAVA, SKILL_SPRING, SKILL_POSTGRES);
  private static final List<String> SKILLS_WITH_DUPLICATE = List.of(SKILL_JAVA, SKILL_JAVA,
      SKILL_SPRING);
  private static final String JOB_SKILLS_CONCATENATED = SKILL_JAVA + "," + SKILL_SPRING;

  private static final String JOB_ID = "job-1";
  private static final String JOB_ID_NEWEST = "newest";
  private static final String JOB_ID_OLDEST = "oldest";
  private static final String JOB_URL = "https://lynq.ai/jobs/1";
  private static final LocalDate CREATED_ON = LocalDate.of(2026, 6, 30);
  private static final Long TOTAL_SEEN = 0L;

  private static final String COMPANY_ID = "company-1";
  private static final String COMPANY_NAME = "Lynq";
  private static final String COMPANY_ABOUT = "We hire";
  private static final Integer COMPANY_SIZE = 42;
  private static final String COMPANY_IMAGE_PATH = "https://cdn/company.png";
  private static final String COMPANY_IMAGE_URL = "https://presigned/company.png";

  private static final String POSTER_ID = "user-1";
  private static final String POSTER_FULL_NAME = "Jane Doe";
  private static final String POSTER_CURRENT_POSITION = "CTO";
  private static final String POSTER_IMAGE_PATH = "https://cdn/user.png";
  private static final String POSTER_IMAGE_URL = "https://presigned/user.png";

  private static final String APPLICATION_ID = "application-1";
  private static final String APPLICATION_ID_NEWEST = "application-newest";
  private static final String APPLICATION_ID_OLDEST = "application-oldest";
  private static final String CANDIDATE_ID = "candidate-1";
  private static final String CANDIDATE_FULL_NAME = "John Candidate";
  private static final String CANDIDATE_CURRENT_POSITION = "Backend Engineer";
  private static final String CANDIDATE_IMAGE_PATH = "lynq/users/candidate-1/profile/pic.png";
  private static final String CANDIDATE_IMAGE_URL = "https://presigned/candidate.png";
  private static final LocalDate APPLIED_ON = LocalDate.of(2026, 7, 17);
  private static final String CANDIDATE_JOB_SKILLS = SKILL_JAVA + "," + SKILL_SPRING;
  private static final String CANDIDATE_MATCHING_SKILLS = SKILL_JAVA;
  private static final Integer CANDIDATE_LYNQ_SCORE = 50;

  private static final String RAW_FILTER_VALUE = "  Java  ";
  private static final String NORMALIZED_FILTER_VALUE = "Java";

  private static final Pageable DEFAULT_PAGEABLE = PageRequest.of(0, 20);

  private static final String AUTHENTICATED_USER_NOT_FOUND = "Authenticated user not found";
  private static final String ONLY_COMPANY_USERS_CAN_CREATE_JOBS =
      "Only users of type COMPANY can create jobs";
  private static final String USER_NOT_LINKED_TO_COMPANY = "User is not linked to any company";
  private static final String JOB_POST_NOT_FOUND = "Job post not found";
  private static final String ALREADY_APPLIED_TO_JOB = "User has already applied to this job";
  private static final String ONLY_CANDIDATE_USERS_CAN_APPLY =
      "Only users of type CANDIDATE can apply to jobs";

  @Mock
  private JobPostRepository jobPostRepository;

  @Mock
  private CompanyRepository companyRepository;

  @Mock
  private UserRepository userRepository;

  @Mock
  private UserApplicationJobRepository userApplicationJobRepository;

  @Mock
  private StorageService storageService;

  @Mock
  private SecurityContext securityContext;

  @Mock
  private Authentication authentication;

  private JobService jobService;

  @BeforeEach
  void setUp() {
    jobService = new JobService(jobPostRepository, companyRepository, userRepository,
        userApplicationJobRepository, storageService);
    SecurityContextHolder.setContext(securityContext);
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void createJobPersistsJobBuiltFromParamsUserAndCompany() {
    UserEntity user = companyUser();
    CompanyEntity company = stubAuthenticatedCompanyUserWithCompany(user);
    when(jobPostRepository.save(any(JobPostEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    ArgumentCaptor<JobPostEntity> jobCaptor = ArgumentCaptor.forClass(JobPostEntity.class);

    jobService.createJob(TITLE, DESCRIPTION, WORK_TYPE, SALARY_RANGE_DOWN, SALARY_RANGE_TOP,
        JOB_POST_TYPE, NO_SKILLS);

    verify(jobPostRepository).save(jobCaptor.capture());
    JobPostEntity saved = jobCaptor.getValue();
    assertThat(saved.getTitle(), is(TITLE));
    assertThat(saved.getDescription(), is(DESCRIPTION));
    assertThat(saved.getWorkType(), is(WORK_TYPE));
    assertThat(saved.getSalaryRangeDown(), is(SALARY_RANGE_DOWN));
    assertThat(saved.getSalaryRangeTop(), is(SALARY_RANGE_TOP));
    assertThat(saved.getJobPostSource(), is(JOB_POST_TYPE));
    assertThat(saved.getCreatedOn(), is(LocalDate.now()));
    assertThat(saved.getCreatedByUser(), is(sameInstance(user)));
    assertThat(saved.getCompany(), is(sameInstance(company)));
    assertThat(saved.getId(), is(notNullValue()));
    assertThat(saved.getSkills(), is(empty()));
  }

  @Test
  void createJobPersistsJobSkillsWhenSkillsProvided() {
    stubAuthenticatedCompanyUserWithCompany(companyUser());
    when(jobPostRepository.save(any(JobPostEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    ArgumentCaptor<JobPostEntity> jobCaptor = ArgumentCaptor.forClass(JobPostEntity.class);

    jobService.createJob(TITLE, DESCRIPTION, WORK_TYPE, SALARY_RANGE_DOWN, SALARY_RANGE_TOP,
        JOB_POST_TYPE, SKILLS);

    verify(jobPostRepository).save(jobCaptor.capture());
    JobPostEntity saved = jobCaptor.getValue();
    assertThat(saved.getSkills(), hasSize(SKILLS.size()));
    assertThat(saved.getSkills().stream().map(JobPostSkillEntity::getSkill).toList(),
        contains(SKILL_JAVA, SKILL_SPRING, SKILL_POSTGRES));
    saved.getSkills().forEach(skill -> {
      assertThat(skill.getId(), is(notNullValue()));
      assertThat(skill.getJobPost(), is(sameInstance(saved)));
    });
  }

  @Test
  void createJobDeduplicatesSkillsBeforePersisting() {
    stubAuthenticatedCompanyUserWithCompany(companyUser());
    when(jobPostRepository.save(any(JobPostEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    ArgumentCaptor<JobPostEntity> jobCaptor = ArgumentCaptor.forClass(JobPostEntity.class);

    jobService.createJob(TITLE, DESCRIPTION, WORK_TYPE, SALARY_RANGE_DOWN, SALARY_RANGE_TOP,
        JOB_POST_TYPE, SKILLS_WITH_DUPLICATE);

    verify(jobPostRepository).save(jobCaptor.capture());
    assertThat(jobCaptor.getValue().getSkills().stream().map(JobPostSkillEntity::getSkill).toList(),
        contains(SKILL_JAVA, SKILL_SPRING));
  }

  @Test
  void createJobGeneratesUuidJobId() {
    stubAuthenticatedCompanyUserWithCompany(companyUser());
    when(jobPostRepository.save(any(JobPostEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    ArgumentCaptor<JobPostEntity> jobCaptor = ArgumentCaptor.forClass(JobPostEntity.class);

    jobService.createJob(TITLE, DESCRIPTION, WORK_TYPE, SALARY_RANGE_DOWN, SALARY_RANGE_TOP,
        JOB_POST_TYPE, NO_SKILLS);

    verify(jobPostRepository).save(jobCaptor.capture());
    assertThat(UUID.fromString(jobCaptor.getValue().getId()), is(notNullValue()));
  }

  @Test
  void createJobReturnsEntityProducedByRepository() {
    stubAuthenticatedCompanyUserWithCompany(companyUser());
    JobPostEntity persisted = JobPostEntity.builder().id(USER_ID).build();
    when(jobPostRepository.save(any(JobPostEntity.class))).thenReturn(persisted);

    JobPostEntity result = jobService.createJob(TITLE, DESCRIPTION, WORK_TYPE, SALARY_RANGE_DOWN,
        SALARY_RANGE_TOP, JOB_POST_TYPE, NO_SKILLS);

    assertThat(result, is(sameInstance(persisted)));
  }

  @Test
  void createJobThrowsBadRequestWhenAuthenticatedUserNotFound() {
    stubAuthenticatedPrincipal();
    when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

    BadRequestException exception = assertThrows(BadRequestException.class,
        () -> jobService.createJob(TITLE, DESCRIPTION, WORK_TYPE, SALARY_RANGE_DOWN,
            SALARY_RANGE_TOP, JOB_POST_TYPE, NO_SKILLS));
    assertThat(exception.getMessage(), is(AUTHENTICATED_USER_NOT_FOUND));
    verify(jobPostRepository, never()).save(any());
  }

  @Test
  void createJobThrowsBadRequestWhenUserIsNotCompanyType() {
    UserEntity candidate = UserEntity.builder().id(USER_ID).type(UserType.CANDIDATE).build();
    stubAuthenticatedPrincipal();
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(candidate));

    BadRequestException exception = assertThrows(BadRequestException.class,
        () -> jobService.createJob(TITLE, DESCRIPTION, WORK_TYPE, SALARY_RANGE_DOWN,
            SALARY_RANGE_TOP, JOB_POST_TYPE, NO_SKILLS));
    assertThat(exception.getMessage(), is(ONLY_COMPANY_USERS_CAN_CREATE_JOBS));
    verify(jobPostRepository, never()).save(any());
  }

  @Test
  void createJobThrowsBadRequestWhenUserNotLinkedToCompany() {
    UserEntity user = companyUser();
    stubAuthenticatedPrincipal();
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
    when(companyRepository.findByOwner(user)).thenReturn(Optional.empty());

    BadRequestException exception = assertThrows(BadRequestException.class,
        () -> jobService.createJob(TITLE, DESCRIPTION, WORK_TYPE, SALARY_RANGE_DOWN,
            SALARY_RANGE_TOP, JOB_POST_TYPE, NO_SKILLS));
    assertThat(exception.getMessage(), is(USER_NOT_LINKED_TO_COMPANY));
    verify(jobPostRepository, never()).save(any());
  }

  @Test
  void searchAvailableJobsMapsProjectionFieldsIncludingCompanyAndPoster() {
    JobFilter filter = new JobFilter(null);
    Pageable pageable = PageRequest.of(0, 20);
    stubAuthenticatedUser(candidateUser(List.of(SKILL_JAVA, SKILL_SPRING)));
    JobWithDetailsProjection projection = new JobWithDetailsProjection(
        JOB_ID, TITLE, DESCRIPTION, WORK_TYPE, SALARY_RANGE_DOWN, SALARY_RANGE_TOP,
        JOB_URL, JOB_POST_TYPE, CREATED_ON, TOTAL_SEEN,
        COMPANY_ID, COMPANY_NAME, COMPANY_ABOUT, COMPANY_SIZE, COMPANY_IMAGE_PATH,
        POSTER_ID, POSTER_FULL_NAME, POSTER_IMAGE_PATH, POSTER_CURRENT_POSITION,
        JOB_SKILLS_CONCATENATED);
    when(jobPostRepository.searchAvailableJobs(null, pageable))
        .thenReturn(new PageImpl<>(List.of(projection), pageable, 1));
    when(storageService.obtainProfilePreSignedUrl(COMPANY_IMAGE_PATH))
        .thenReturn(COMPANY_IMAGE_URL);
    when(storageService.obtainProfilePreSignedUrl(POSTER_IMAGE_PATH))
        .thenReturn(POSTER_IMAGE_URL);

    PagedRestResponse<GetJobRestResponse> result = jobService.searchAvailableJobs(filter, pageable);

    assertThat(result.getContent(), hasSize(1));
    GetJobRestResponse job = result.getContent().get(0);
    assertThat(job.getJobId(), is(JOB_ID));
    assertThat(job.getTitle(), is(TITLE));
    assertThat(job.getDescription(), is(DESCRIPTION));
    assertThat(job.getWorkType(), is(WORK_TYPE));
    assertThat(job.getSalaryRangeDown(), is(SALARY_RANGE_DOWN));
    assertThat(job.getSalaryRangeTop(), is(SALARY_RANGE_TOP));
    assertThat(job.getJobUrl(), is(JOB_URL));
    assertThat(job.getJobPostSource(), is(JOB_POST_TYPE));
    assertThat(job.getCreatedOn(), is(CREATED_ON));
    assertThat(job.getCompany().getId(), is(COMPANY_ID));
    assertThat(job.getCompany().getName(), is(COMPANY_NAME));
    assertThat(job.getCompany().getAbout(), is(COMPANY_ABOUT));
    assertThat(job.getCompany().getSize(), is(COMPANY_SIZE));
    assertThat(job.getCompany().getProfileImageUrl(), is(COMPANY_IMAGE_URL));
    assertThat(job.getPostedBy().getId(), is(POSTER_ID));
    assertThat(job.getPostedBy().getFullName(), is(POSTER_FULL_NAME));
    assertThat(job.getPostedBy().getProfileImageUrl(), is(POSTER_IMAGE_URL));
    assertThat(job.getPostedBy().getCurrentPosition(), is(POSTER_CURRENT_POSITION));
    assertThat(job.getSkills(), contains(SKILL_JAVA, SKILL_SPRING));
    assertThat(job.getLynqScore(), is(100));
  }

  @Test
  void searchAvailableJobsNormalizesBlankFiltersAndForwardsPageableToRepository() {
    JobFilter filter = new JobFilter(RAW_FILTER_VALUE);
    Pageable pageable = PageRequest.of(1, 5);
    stubAuthenticatedUser(candidateUser(null));
    when(jobPostRepository.searchAvailableJobs(NORMALIZED_FILTER_VALUE, pageable))
        .thenReturn(new PageImpl<>(List.of(), pageable, 0));

    jobService.searchAvailableJobs(filter, pageable);

    verify(jobPostRepository).searchAvailableJobs(NORMALIZED_FILTER_VALUE, pageable);
  }

  @Test
  void searchAvailableJobsMapsPaginationMetadataAndPreservesOrder() {
    JobFilter filter = new JobFilter(null);
    Pageable pageable = PageRequest.of(1, 2);
    stubAuthenticatedUser(candidateUser(null));
    Page<JobWithDetailsProjection> page = new PageImpl<>(
        List.of(projectionWithId(JOB_ID_NEWEST), projectionWithId(JOB_ID_OLDEST)), pageable, 6);
    when(jobPostRepository.searchAvailableJobs(null, pageable))
        .thenReturn(page);

    PagedRestResponse<GetJobRestResponse> result = jobService.searchAvailableJobs(filter, pageable);

    assertThat(result.getPage(), is(1));
    assertThat(result.getSize(), is(2));
    assertThat(result.getTotalElements(), is(6L));
    assertThat(result.getTotalPages(), is(3));
    assertThat(result.isHasNext(), is(true));
    assertThat(result.isHasPrevious(), is(true));
    assertThat(result.getContent().stream().map(GetJobRestResponse::getJobId).toList(),
        contains(JOB_ID_NEWEST, JOB_ID_OLDEST));
  }

  @Test
  void searchAvailableJobsReturnsEmptyContentWhenRepositoryReturnsNoJobs() {
    JobFilter filter = new JobFilter(null);
    Pageable pageable = PageRequest.of(0, 20);
    stubAuthenticatedUser(candidateUser(null));
    when(jobPostRepository.searchAvailableJobs(null, pageable))
        .thenReturn(new PageImpl<>(List.of(), pageable, 0));

    PagedRestResponse<GetJobRestResponse> result = jobService.searchAvailableJobs(filter, pageable);

    assertThat(result.getContent(), is(empty()));
    assertThat(result.getTotalElements(), is(0L));
  }

  @Test
  void searchAvailableJobsScoresLynqAsPercentageOfMatchingJobSkills() {
    stubAuthenticatedUser(candidateUser(List.of(SKILL_JAVA)));
    stubSingleJob(JOB_SKILLS_CONCATENATED);

    assertThat(searchSingleJobLynqScore(), is(50));
  }

  @Test
  void searchAvailableJobsMatchesSkillsCaseInsensitively() {
    stubAuthenticatedUser(candidateUser(List.of("java", "SPRING")));
    stubSingleJob(JOB_SKILLS_CONCATENATED);

    assertThat(searchSingleJobLynqScore(), is(100));
  }

  @Test
  void searchAvailableJobsDoesNotScoreLynqWhenUserIsCompany() {
    stubAuthenticatedUser(companyUser());
    stubSingleJob(JOB_SKILLS_CONCATENATED);

    assertThat(searchSingleJobLynqScore(), is(nullValue()));
  }

  @Test
  void searchAvailableJobsDoesNotScoreLynqWhenCandidateHasNoSkills() {
    stubAuthenticatedUser(candidateUser(null));
    stubSingleJob(JOB_SKILLS_CONCATENATED);

    assertThat(searchSingleJobLynqScore(), is(nullValue()));
  }

  @Test
  void searchAvailableJobsDoesNotScoreLynqWhenJobHasNoSkills() {
    stubAuthenticatedUser(candidateUser(List.of(SKILL_JAVA)));
    stubSingleJob(null);

    assertThat(searchSingleJobLynqScore(), is(nullValue()));
  }

  @Test
  void increaseSeenIncrementsCounterAndReturnsRefreshedJob() {
    JobPostEntity refreshed = JobPostEntity.builder().id(JOB_ID).totalSeen(5L).build();
    when(jobPostRepository.increaseTotalSeen(JOB_ID)).thenReturn(1);
    when(jobPostRepository.findById(JOB_ID)).thenReturn(Optional.of(refreshed));

    JobPostEntity result = jobService.increaseSeen(JOB_ID);

    assertThat(result, is(sameInstance(refreshed)));
    assertThat(result.getTotalSeen(), is(5L));
    verify(jobPostRepository).increaseTotalSeen(JOB_ID);
  }

  @Test
  void increaseSeenThrowsNotFoundWhenNoRowIsUpdated() {
    when(jobPostRepository.increaseTotalSeen(JOB_ID)).thenReturn(0);

    NotFoundException exception = assertThrows(NotFoundException.class,
        () -> jobService.increaseSeen(JOB_ID));
    assertThat(exception.getMessage(), is(JOB_POST_NOT_FOUND));
    verify(jobPostRepository, never()).findById(any());
  }

  @Test
  void increaseSeenThrowsNotFoundWhenJobDisappearsAfterUpdate() {
    when(jobPostRepository.increaseTotalSeen(JOB_ID)).thenReturn(1);
    when(jobPostRepository.findById(JOB_ID)).thenReturn(Optional.empty());

    NotFoundException exception = assertThrows(NotFoundException.class,
        () -> jobService.increaseSeen(JOB_ID));
    assertThat(exception.getMessage(), is(JOB_POST_NOT_FOUND));
  }

  @Test
  void applyToJobPersistsApplicationForAuthenticatedUserAndJob() {
    UserEntity user = candidateUser(null);
    JobPostEntity job = JobPostEntity.builder().id(JOB_ID).build();
    stubAuthenticatedUser(user);
    when(jobPostRepository.findById(JOB_ID)).thenReturn(Optional.of(job));
    when(userApplicationJobRepository.existsByJobIdAndUserId(JOB_ID, USER_ID))
        .thenReturn(false);
    when(userApplicationJobRepository.save(any(UserApplicationJobEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    ArgumentCaptor<UserApplicationJobEntity> applicationCaptor =
        ArgumentCaptor.forClass(UserApplicationJobEntity.class);

    UserApplicationJobEntity result = jobService.applyToJob(JOB_ID);

    verify(userApplicationJobRepository).save(applicationCaptor.capture());
    UserApplicationJobEntity saved = applicationCaptor.getValue();
    assertThat(UUID.fromString(saved.getId()), is(notNullValue()));
    assertThat(saved.getJobPost(), is(sameInstance(job)));
    assertThat(saved.getUser(), is(sameInstance(user)));
    assertThat(saved.getAppliedOn(), is(LocalDate.now()));
    assertThat(result, is(sameInstance(saved)));
  }

  @Test
  void applyToJobThrowsNotFoundWhenJobDoesNotExist() {
    stubAuthenticatedUser(candidateUser(null));
    when(jobPostRepository.findById(JOB_ID)).thenReturn(Optional.empty());

    NotFoundException exception = assertThrows(NotFoundException.class,
        () -> jobService.applyToJob(JOB_ID));
    assertThat(exception.getMessage(), is(JOB_POST_NOT_FOUND));
    verify(userApplicationJobRepository, never()).save(any());
  }

  @Test
  void applyToJobThrowsAlreadyAppliedWhenUserAlreadyAppliedToJob() {
    stubAuthenticatedUser(candidateUser(null));
    when(jobPostRepository.findById(JOB_ID))
        .thenReturn(Optional.of(JobPostEntity.builder().id(JOB_ID).build()));
    when(userApplicationJobRepository.existsByJobIdAndUserId(JOB_ID, USER_ID))
        .thenReturn(true);

    AlreadyAppliedToJobException exception = assertThrows(AlreadyAppliedToJobException.class,
        () -> jobService.applyToJob(JOB_ID));
    assertThat(exception.getMessage(), is(ALREADY_APPLIED_TO_JOB));
    verify(userApplicationJobRepository, never()).save(any());
  }

  @Test
  void applyToJobThrowsBadRequestWhenUserIsNotCandidate() {
    stubAuthenticatedUser(companyUser());

    BadRequestException exception = assertThrows(BadRequestException.class,
        () -> jobService.applyToJob(JOB_ID));
    assertThat(exception.getMessage(), is(ONLY_CANDIDATE_USERS_CAN_APPLY));
    verify(jobPostRepository, never()).findById(any());
    verify(userApplicationJobRepository, never()).save(any());
  }

  @Test
  void getJobCandidatesMapsProjectionFieldsAndGeneratesPresignedImageUrl() {
    when(storageService.obtainProfilePreSignedUrl(CANDIDATE_IMAGE_PATH))
        .thenReturn(CANDIDATE_IMAGE_URL);
    when(userApplicationJobRepository.findCandidatesByJobId(JOB_ID, DEFAULT_PAGEABLE))
        .thenReturn(new PageImpl<>(List.of(candidateProjection(APPLICATION_ID)),
            DEFAULT_PAGEABLE, 1));

    PagedRestResponse<JobCandidateResponse> result =
        jobService.getJobCandidates(JOB_ID, DEFAULT_PAGEABLE);

    assertThat(result.getContent(), hasSize(1));
    JobCandidateResponse candidate = result.getContent().get(0);
    assertThat(candidate.getId(), is(APPLICATION_ID));
    assertThat(candidate.getUserId(), is(CANDIDATE_ID));
    assertThat(candidate.getJobId(), is(JOB_ID));
    assertThat(candidate.getUserFullName(), is(CANDIDATE_FULL_NAME));
    assertThat(candidate.getUserProfileImage(), is(CANDIDATE_IMAGE_URL));
    assertThat(candidate.getUserCurrentPosition(), is(CANDIDATE_CURRENT_POSITION));
    assertThat(candidate.getUserAppliedOn(), is(APPLIED_ON));
    assertThat(candidate.getLynqScore(), is(CANDIDATE_LYNQ_SCORE));
  }

  @Test
  void getJobCandidatesScoresLynqAsPercentageOfMatchingJobSkills() {
    JobCandidateProjection projection = new JobCandidateProjection(APPLICATION_ID, CANDIDATE_ID,
        JOB_ID, CANDIDATE_FULL_NAME, CANDIDATE_IMAGE_PATH, CANDIDATE_CURRENT_POSITION, APPLIED_ON,
        CANDIDATE_JOB_SKILLS, CANDIDATE_JOB_SKILLS);
    when(userApplicationJobRepository.findCandidatesByJobId(JOB_ID, DEFAULT_PAGEABLE))
        .thenReturn(new PageImpl<>(List.of(projection), DEFAULT_PAGEABLE, 1));

    JobCandidateResponse candidate =
        jobService.getJobCandidates(JOB_ID, DEFAULT_PAGEABLE).getContent().get(0);

    assertThat(candidate.getLynqScore(), is(100));
  }

  @Test
  void getJobCandidatesLeavesLynqScoreNullWhenCandidateHasNoSkills() {
    JobCandidateProjection projection = new JobCandidateProjection(APPLICATION_ID, CANDIDATE_ID,
        JOB_ID, CANDIDATE_FULL_NAME, CANDIDATE_IMAGE_PATH, CANDIDATE_CURRENT_POSITION, APPLIED_ON,
        CANDIDATE_JOB_SKILLS, null);
    when(userApplicationJobRepository.findCandidatesByJobId(JOB_ID, DEFAULT_PAGEABLE))
        .thenReturn(new PageImpl<>(List.of(projection), DEFAULT_PAGEABLE, 1));

    JobCandidateResponse candidate =
        jobService.getJobCandidates(JOB_ID, DEFAULT_PAGEABLE).getContent().get(0);

    assertThat(candidate.getLynqScore(), is(nullValue()));
  }

  @Test
  void getJobCandidatesLeavesLynqScoreNullWhenJobHasNoSkills() {
    JobCandidateProjection projection = new JobCandidateProjection(APPLICATION_ID, CANDIDATE_ID,
        JOB_ID, CANDIDATE_FULL_NAME, CANDIDATE_IMAGE_PATH, CANDIDATE_CURRENT_POSITION, APPLIED_ON,
        null, CANDIDATE_MATCHING_SKILLS);
    when(userApplicationJobRepository.findCandidatesByJobId(JOB_ID, DEFAULT_PAGEABLE))
        .thenReturn(new PageImpl<>(List.of(projection), DEFAULT_PAGEABLE, 1));

    JobCandidateResponse candidate =
        jobService.getJobCandidates(JOB_ID, DEFAULT_PAGEABLE).getContent().get(0);

    assertThat(candidate.getLynqScore(), is(nullValue()));
  }

  @Test
  void getJobCandidatesLeavesProfileImageNullWhenPathIsBlank() {
    JobCandidateProjection projection = new JobCandidateProjection(APPLICATION_ID, CANDIDATE_ID,
        JOB_ID, CANDIDATE_FULL_NAME, null, CANDIDATE_CURRENT_POSITION, APPLIED_ON,
        CANDIDATE_JOB_SKILLS, CANDIDATE_MATCHING_SKILLS);
    when(userApplicationJobRepository.findCandidatesByJobId(JOB_ID, DEFAULT_PAGEABLE))
        .thenReturn(new PageImpl<>(List.of(projection), DEFAULT_PAGEABLE, 1));

    JobCandidateResponse candidate =
        jobService.getJobCandidates(JOB_ID, DEFAULT_PAGEABLE).getContent().get(0);

    assertThat(candidate.getUserProfileImage(), is(nullValue()));
    verify(storageService, never()).obtainProfilePreSignedUrl(any());
  }

  @Test
  void getJobCandidatesMapsPaginationMetadataAndPreservesOrder() {
    Pageable pageable = PageRequest.of(1, 2);
    Page<JobCandidateProjection> page = new PageImpl<>(
        List.of(candidateProjection(APPLICATION_ID_NEWEST),
            candidateProjection(APPLICATION_ID_OLDEST)), pageable, 6);
    when(userApplicationJobRepository.findCandidatesByJobId(JOB_ID, pageable)).thenReturn(page);

    PagedRestResponse<JobCandidateResponse> result = jobService.getJobCandidates(JOB_ID, pageable);

    assertThat(result.getPage(), is(1));
    assertThat(result.getSize(), is(2));
    assertThat(result.getTotalElements(), is(6L));
    assertThat(result.getTotalPages(), is(3));
    assertThat(result.isHasNext(), is(true));
    assertThat(result.isHasPrevious(), is(true));
    assertThat(result.getContent().stream().map(JobCandidateResponse::getId).toList(),
        contains(APPLICATION_ID_NEWEST, APPLICATION_ID_OLDEST));
  }

  @Test
  void getJobCandidatesReturnsEmptyContentWhenNoCandidates() {
    when(userApplicationJobRepository.findCandidatesByJobId(JOB_ID, DEFAULT_PAGEABLE))
        .thenReturn(new PageImpl<>(List.of(), DEFAULT_PAGEABLE, 0));

    PagedRestResponse<JobCandidateResponse> result =
        jobService.getJobCandidates(JOB_ID, DEFAULT_PAGEABLE);

    assertThat(result.getContent(), is(empty()));
    assertThat(result.getTotalElements(), is(0L));
  }

  private JobCandidateProjection candidateProjection(String applicationId) {
    return new JobCandidateProjection(applicationId, CANDIDATE_ID, JOB_ID, CANDIDATE_FULL_NAME,
        CANDIDATE_IMAGE_PATH, CANDIDATE_CURRENT_POSITION, APPLIED_ON, CANDIDATE_JOB_SKILLS,
        CANDIDATE_MATCHING_SKILLS);
  }

  private JobWithDetailsProjection projectionWithId(String jobId) {
    return new JobWithDetailsProjection(
        jobId, TITLE, DESCRIPTION, WORK_TYPE, SALARY_RANGE_DOWN, SALARY_RANGE_TOP,
        null, JOB_POST_TYPE, CREATED_ON, TOTAL_SEEN,
        COMPANY_ID, COMPANY_NAME, COMPANY_ABOUT, COMPANY_SIZE, COMPANY_IMAGE_PATH,
        POSTER_ID, POSTER_FULL_NAME, POSTER_IMAGE_PATH, POSTER_CURRENT_POSITION, null);
  }

  private UserEntity companyUser() {
    return UserEntity.builder().id(USER_ID).type(UserType.COMPANY).build();
  }

  private UserEntity candidateUser(List<String> skillNames) {
    UserEntity user = UserEntity.builder().id(USER_ID).type(UserType.CANDIDATE).build();
    if (skillNames != null) {
      user.setSkills(skillNames.stream()
          .map(name -> UserSkillsEntity.builder().skill(name).user(user).build())
          .collect(Collectors.toList()));
    }
    return user;
  }

  private void stubAuthenticatedUser(UserEntity user) {
    stubAuthenticatedPrincipal();
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
  }

  private void stubSingleJob(String concatenatedSkills) {
    JobWithDetailsProjection projection = new JobWithDetailsProjection(
        JOB_ID, TITLE, DESCRIPTION, WORK_TYPE, SALARY_RANGE_DOWN, SALARY_RANGE_TOP,
        JOB_URL, JOB_POST_TYPE, CREATED_ON, TOTAL_SEEN,
        COMPANY_ID, COMPANY_NAME, COMPANY_ABOUT, COMPANY_SIZE, null,
        POSTER_ID, POSTER_FULL_NAME, null, POSTER_CURRENT_POSITION, concatenatedSkills);
    when(jobPostRepository.searchAvailableJobs(null, DEFAULT_PAGEABLE))
        .thenReturn(new PageImpl<>(List.of(projection), DEFAULT_PAGEABLE, 1));
  }

  private Integer searchSingleJobLynqScore() {
    return jobService.searchAvailableJobs(new JobFilter(null), DEFAULT_PAGEABLE)
        .getContent().get(0).getLynqScore();
  }

  private void stubAuthenticatedPrincipal() {
    when(securityContext.getAuthentication()).thenReturn(authentication);
    when(authentication.getPrincipal())
        .thenReturn(new LynqUserPrincipal(USER_ID, USERNAME, EMAIL));
  }

  private CompanyEntity stubAuthenticatedCompanyUserWithCompany(UserEntity user) {
    CompanyEntity company = CompanyEntity.builder().id(COMPANY_ID).owner(user).build();
    stubAuthenticatedPrincipal();
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
    when(companyRepository.findByOwner(user)).thenReturn(Optional.of(company));
    return company;
  }
}
