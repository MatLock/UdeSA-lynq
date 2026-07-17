package com.lynq.backend.controller.impl;

import com.lynq.backend.controller.request.CreateJobRequest;
import com.lynq.backend.controller.response.ApplyJobRestResponse;
import com.lynq.backend.controller.response.CreateJobRestResponse;
import com.lynq.backend.controller.response.GetJobRestResponse;
import com.lynq.backend.controller.response.GlobalRestResponse;
import com.lynq.backend.controller.response.JobCandidateResponse;
import com.lynq.backend.controller.response.PagedRestResponse;
import com.lynq.backend.enums.JobPostSource;
import com.lynq.backend.enums.WorkType;
import com.lynq.backend.model.CompanyEntity;
import com.lynq.backend.model.JobPostEntity;
import com.lynq.backend.model.JobPostSkillEntity;
import com.lynq.backend.model.UserApplicationJobEntity;
import com.lynq.backend.model.UserEntity;
import com.lynq.backend.service.JobFilter;
import com.lynq.backend.service.JobService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobControllerImplTest {

  private static final String JOB_ID = "018f9c3a-2b1d-7c4e-9a6f-1e2d3c4b5a60";
  private static final String COMPANY_ID = "018f9c3a-2b1d-7c4e-9a6f-aaaaaaaaaaaa";
  private static final String USER_ID = "550e8400-e29b-41d4-a716-446655440000";
  private static final String TITLE = "Senior Backend Engineer";
  private static final String DESCRIPTION = "Build and scale the Lynq hiring platform.";
  private static final WorkType WORK_TYPE = WorkType.REMOTE;
  private static final Integer SALARY_RANGE_DOWN = 80000;
  private static final Integer SALARY_RANGE_TOP = 120000;
  private static final JobPostSource JOB_POST_TYPE = JobPostSource.LYNQ;
  private static final String SKILL_JAVA = "Java";
  private static final String SKILL_SPRING = "Spring";
  private static final List<String> SKILLS = List.of(SKILL_JAVA, SKILL_SPRING);
  private static final LocalDate CREATED_ON = LocalDate.of(2026, 6, 26);
  private static final String RAW_FILTER_VALUE = "  Java  ";
  private static final String NORMALIZED_FILTER_VALUE = "Java";
  private static final int REQUESTED_PAGE = 2;
  private static final int REQUESTED_SIZE = 15;
  private static final int DEFAULT_PAGE = 0;
  private static final int DEFAULT_SIZE = 20;
  private static final Long SEEN_COUNT = 7L;
  private static final String APPLICATION_ID = "018f9c3a-2b1d-7c4e-9a6f-bbbbbbbbbbbb";
  private static final LocalDate APPLIED_ON = LocalDate.of(2026, 7, 17);

  @Mock
  private JobService jobService;

  @Mock
  private CreateJobRequest request;

  private JobControllerImpl jobController;

  @BeforeEach
  void setUp() {
    jobController = new JobControllerImpl(jobService);
    lenient().when(request.getTitle()).thenReturn(TITLE);
    lenient().when(request.getDescription()).thenReturn(DESCRIPTION);
    lenient().when(request.getWorkType()).thenReturn(WORK_TYPE);
    lenient().when(request.getSalaryRangeDown()).thenReturn(SALARY_RANGE_DOWN);
    lenient().when(request.getSalaryRangeTop()).thenReturn(SALARY_RANGE_TOP);
    lenient().when(request.getJobPostSource()).thenReturn(JOB_POST_TYPE);
    lenient().when(request.getSkills()).thenReturn(SKILLS);
    lenient().when(jobService.createJob(TITLE, DESCRIPTION, WORK_TYPE, SALARY_RANGE_DOWN,
        SALARY_RANGE_TOP, JOB_POST_TYPE, SKILLS)).thenReturn(savedJob());
  }

  @Test
  void createJobDelegatesToServiceWithRequestFields() {
    jobController.createJob(request);

    verify(jobService).createJob(TITLE, DESCRIPTION, WORK_TYPE, SALARY_RANGE_DOWN, SALARY_RANGE_TOP,
        JOB_POST_TYPE, SKILLS);
  }

  @Test
  void createJobRespondsWithCreatedStatus() {
    ResponseEntity<GlobalRestResponse<CreateJobRestResponse>> response =
        jobController.createJob(request);

    assertThat(response.getStatusCode(), is(HttpStatus.CREATED));
  }

  @Test
  void createJobWrapsSuccessfulResponseBody() {
    ResponseEntity<GlobalRestResponse<CreateJobRestResponse>> response =
        jobController.createJob(request);

    GlobalRestResponse<CreateJobRestResponse> body = response.getBody();
    assertThat(body, is(notNullValue()));
    assertThat(body.isSuccess(), is(true));
  }

  @Test
  void createJobMapsSavedJobIntoResponseData() {
    ResponseEntity<GlobalRestResponse<CreateJobRestResponse>> response =
        jobController.createJob(request);

    CreateJobRestResponse data = response.getBody().getData();
    assertThat(data.getJobId(), is(JOB_ID));
    assertThat(data.getTitle(), is(TITLE));
    assertThat(data.getDescription(), is(DESCRIPTION));
    assertThat(data.getWorkType(), is(WORK_TYPE));
    assertThat(data.getSalaryRangeDown(), is(SALARY_RANGE_DOWN));
    assertThat(data.getSalaryRangeTop(), is(SALARY_RANGE_TOP));
    assertThat(data.getJobPostSource(), is(JOB_POST_TYPE));
    assertThat(data.getCreatedOn(), is(CREATED_ON));
    assertThat(data.getCompanyId(), is(COMPANY_ID));
    assertThat(data.getCreatedByUserId(), is(USER_ID));
    assertThat(data.getSkills(), contains(SKILL_JAVA, SKILL_SPRING));
  }

  @Test
  void getJobsDelegatesToServiceWithNormalizedFilterAndPageable() {
    when(jobService.searchAvailableJobs(any(JobFilter.class), any(Pageable.class)))
        .thenReturn(emptyPaged());

    jobController.getJobs(REQUESTED_PAGE, REQUESTED_SIZE, RAW_FILTER_VALUE);

    ArgumentCaptor<JobFilter> filterCaptor = ArgumentCaptor.forClass(JobFilter.class);
    ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
    verify(jobService).searchAvailableJobs(filterCaptor.capture(), pageableCaptor.capture());

    JobFilter filter = filterCaptor.getValue();
    assertThat(filter.filterValue(), is(NORMALIZED_FILTER_VALUE));

    Pageable pageable = pageableCaptor.getValue();
    assertThat(pageable.getPageNumber(), is(REQUESTED_PAGE));
    assertThat(pageable.getPageSize(), is(REQUESTED_SIZE));
  }

  @Test
  void getJobsRespondsWithOkStatus() {
    when(jobService.searchAvailableJobs(any(JobFilter.class), any(Pageable.class)))
        .thenReturn(emptyPaged());

    ResponseEntity<GlobalRestResponse<PagedRestResponse<GetJobRestResponse>>> response =
        jobController.getJobs(DEFAULT_PAGE, DEFAULT_SIZE, null);

    assertThat(response.getStatusCode(), is(HttpStatus.OK));
  }

  @Test
  void getJobsWrapsServiceResultInSuccessfulEnvelope() {
    GetJobRestResponse job = GetJobRestResponse.builder().jobId(JOB_ID).build();
    PagedRestResponse<GetJobRestResponse> paged = PagedRestResponse.<GetJobRestResponse>builder()
        .content(List.of(job))
        .page(DEFAULT_PAGE)
        .size(DEFAULT_SIZE)
        .totalElements(1)
        .totalPages(1)
        .hasNext(false)
        .hasPrevious(false)
        .build();
    when(jobService.searchAvailableJobs(any(JobFilter.class), any(Pageable.class)))
        .thenReturn(paged);

    ResponseEntity<GlobalRestResponse<PagedRestResponse<GetJobRestResponse>>> response =
        jobController.getJobs(DEFAULT_PAGE, DEFAULT_SIZE, null);

    GlobalRestResponse<PagedRestResponse<GetJobRestResponse>> body = response.getBody();
    assertThat(body, is(notNullValue()));
    assertThat(body.isSuccess(), is(true));
    assertThat(body.getData().getContent(), hasSize(1));
    assertThat(body.getData().getContent().get(0).getJobId(), is(JOB_ID));
  }

  @Test
  void increaseSeenDelegatesToServiceWithJobId() {
    when(jobService.increaseSeen(JOB_ID)).thenReturn(seenJob(SEEN_COUNT));

    jobController.increaseSeen(JOB_ID);

    verify(jobService).increaseSeen(JOB_ID);
  }

  @Test
  void increaseSeenRespondsWithOkStatus() {
    when(jobService.increaseSeen(JOB_ID)).thenReturn(seenJob(SEEN_COUNT));

    ResponseEntity<GlobalRestResponse<Long>> response = jobController.increaseSeen(JOB_ID);

    assertThat(response.getStatusCode(), is(HttpStatus.OK));
  }

  @Test
  void increaseSeenWrapsUpdatedCounterInSuccessfulEnvelope() {
    when(jobService.increaseSeen(JOB_ID)).thenReturn(seenJob(SEEN_COUNT));

    ResponseEntity<GlobalRestResponse<Long>> response = jobController.increaseSeen(JOB_ID);

    GlobalRestResponse<Long> body = response.getBody();
    assertThat(body, is(notNullValue()));
    assertThat(body.isSuccess(), is(true));
    assertThat(body.getData(), is(SEEN_COUNT));
  }

  private JobPostEntity seenJob(Long totalSeen) {
    return JobPostEntity.builder().id(JOB_ID).totalSeen(totalSeen).build();
  }

  @Test
  void applyToJobDelegatesToServiceWithJobId() {
    when(jobService.applyToJob(JOB_ID)).thenReturn(application());

    jobController.applyToJob(JOB_ID);

    verify(jobService).applyToJob(JOB_ID);
  }

  @Test
  void applyToJobRespondsWithCreatedStatus() {
    when(jobService.applyToJob(JOB_ID)).thenReturn(application());

    ResponseEntity<GlobalRestResponse<ApplyJobRestResponse>> response =
        jobController.applyToJob(JOB_ID);

    assertThat(response.getStatusCode(), is(HttpStatus.CREATED));
  }

  @Test
  void applyToJobMapsApplicationIntoSuccessfulResponseData() {
    when(jobService.applyToJob(JOB_ID)).thenReturn(application());

    ResponseEntity<GlobalRestResponse<ApplyJobRestResponse>> response =
        jobController.applyToJob(JOB_ID);

    GlobalRestResponse<ApplyJobRestResponse> body = response.getBody();
    assertThat(body, is(notNullValue()));
    assertThat(body.isSuccess(), is(true));
    ApplyJobRestResponse data = body.getData();
    assertThat(data.getApplicationId(), is(APPLICATION_ID));
    assertThat(data.getJobId(), is(JOB_ID));
    assertThat(data.getUserId(), is(USER_ID));
    assertThat(data.getAppliedOn(), is(APPLIED_ON));
  }

  @Test
  void getJobCandidatesDelegatesToServiceWithJobIdAndPageable() {
    when(jobService.getJobCandidates(eq(JOB_ID), any(Pageable.class)))
        .thenReturn(emptyCandidates());

    jobController.getJobCandidates(JOB_ID, REQUESTED_PAGE, REQUESTED_SIZE);

    ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
    verify(jobService).getJobCandidates(eq(JOB_ID), pageableCaptor.capture());
    Pageable pageable = pageableCaptor.getValue();
    assertThat(pageable.getPageNumber(), is(REQUESTED_PAGE));
    assertThat(pageable.getPageSize(), is(REQUESTED_SIZE));
  }

  @Test
  void getJobCandidatesRespondsWithOkStatus() {
    when(jobService.getJobCandidates(eq(JOB_ID), any(Pageable.class)))
        .thenReturn(emptyCandidates());

    ResponseEntity<GlobalRestResponse<PagedRestResponse<JobCandidateResponse>>> response =
        jobController.getJobCandidates(JOB_ID, DEFAULT_PAGE, DEFAULT_SIZE);

    assertThat(response.getStatusCode(), is(HttpStatus.OK));
  }

  @Test
  void getJobCandidatesWrapsServiceResultInSuccessfulEnvelope() {
    JobCandidateResponse candidate = JobCandidateResponse.builder()
        .id(APPLICATION_ID)
        .jobId(JOB_ID)
        .userId(USER_ID)
        .build();
    PagedRestResponse<JobCandidateResponse> paged =
        PagedRestResponse.<JobCandidateResponse>builder().content(List.of(candidate)).build();
    when(jobService.getJobCandidates(eq(JOB_ID), any(Pageable.class))).thenReturn(paged);

    ResponseEntity<GlobalRestResponse<PagedRestResponse<JobCandidateResponse>>> response =
        jobController.getJobCandidates(JOB_ID, DEFAULT_PAGE, DEFAULT_SIZE);

    GlobalRestResponse<PagedRestResponse<JobCandidateResponse>> body = response.getBody();
    assertThat(body, is(notNullValue()));
    assertThat(body.isSuccess(), is(true));
    assertThat(body.getData().getContent(), hasSize(1));
    assertThat(body.getData().getContent().get(0).getId(), is(APPLICATION_ID));
  }

  private PagedRestResponse<JobCandidateResponse> emptyCandidates() {
    return PagedRestResponse.<JobCandidateResponse>builder().content(List.of()).build();
  }

  private UserApplicationJobEntity application() {
    return UserApplicationJobEntity.builder()
        .id(APPLICATION_ID)
        .jobPost(JobPostEntity.builder().id(JOB_ID).build())
        .user(UserEntity.builder().id(USER_ID).build())
        .appliedOn(APPLIED_ON)
        .build();
  }

  private PagedRestResponse<GetJobRestResponse> emptyPaged() {
    return PagedRestResponse.<GetJobRestResponse>builder().content(List.of()).build();
  }

  private JobPostEntity savedJob() {
    JobPostEntity job = JobPostEntity.builder()
        .id(JOB_ID)
        .title(TITLE)
        .description(DESCRIPTION)
        .workType(WORK_TYPE)
        .salaryRangeDown(SALARY_RANGE_DOWN)
        .salaryRangeTop(SALARY_RANGE_TOP)
        .jobPostSource(JOB_POST_TYPE)
        .createdOn(CREATED_ON)
        .createdByUser(UserEntity.builder().id(USER_ID).build())
        .company(CompanyEntity.builder().id(COMPANY_ID).build())
        .build();
    SKILLS.forEach(skill -> job.getSkills().add(
        JobPostSkillEntity.builder().id(skill).jobPost(job).skill(skill).build()));
    return job;
  }
}