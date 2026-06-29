package com.lynq.backend.service;

import com.lynq.backend.enums.JobPostSource;
import com.lynq.backend.enums.UserType;
import com.lynq.backend.enums.WorkType;
import com.lynq.backend.exceptions.BadRequestException;
import com.lynq.backend.model.CompanyEntity;
import com.lynq.backend.model.JobPostEntity;
import com.lynq.backend.model.JobPostSkillEntity;
import com.lynq.backend.model.UserEntity;
import com.lynq.backend.repository.CompanyRepository;
import com.lynq.backend.repository.JobPostRepository;
import com.lynq.backend.repository.UserRepository;
import com.lynq.backend.security.LynqUserPrincipal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
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
  private static final List<String> NO_SKILLS = null;
  private static final List<String> SKILLS = List.of("Java", "Spring", "PostgreSQL");

  @Mock
  private JobPostRepository jobPostRepository;

  @Mock
  private CompanyRepository companyRepository;

  @Mock
  private UserRepository userRepository;

  @Mock
  private SecurityContext securityContext;

  @Mock
  private Authentication authentication;

  private JobService jobService;

  @BeforeEach
  void setUp() {
    jobService = new JobService(jobPostRepository, companyRepository, userRepository);
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
    assertThat(saved.getSkills(), hasSize(3));
    assertThat(saved.getSkills().stream().map(JobPostSkillEntity::getSkill).toList(),
        contains("Java", "Spring", "PostgreSQL"));
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
        JOB_POST_TYPE, List.of("Java", "Java", "Spring"));

    verify(jobPostRepository).save(jobCaptor.capture());
    assertThat(jobCaptor.getValue().getSkills().stream().map(JobPostSkillEntity::getSkill).toList(),
        contains("Java", "Spring"));
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
    assertThat(exception.getMessage(), is("Authenticated user not found"));
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
    assertThat(exception.getMessage(), is("Only users of type COMPANY can create jobs"));
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
    assertThat(exception.getMessage(), is("User is not linked to any company"));
    verify(jobPostRepository, never()).save(any());
  }

  private UserEntity companyUser() {
    return UserEntity.builder().id(USER_ID).type(UserType.COMPANY).build();
  }

  private void stubAuthenticatedPrincipal() {
    when(securityContext.getAuthentication()).thenReturn(authentication);
    when(authentication.getPrincipal())
        .thenReturn(new LynqUserPrincipal(USER_ID, USERNAME, EMAIL));
  }

  private CompanyEntity stubAuthenticatedCompanyUserWithCompany(UserEntity user) {
    CompanyEntity company = CompanyEntity.builder().id("company-id").owner(user).build();
    stubAuthenticatedPrincipal();
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
    when(companyRepository.findByOwner(user)).thenReturn(Optional.of(company));
    return company;
  }
}