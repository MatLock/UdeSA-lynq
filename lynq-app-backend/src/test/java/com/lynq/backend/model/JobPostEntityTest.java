package com.lynq.backend.model;

import com.lynq.backend.enums.JobPostSource;
import com.lynq.backend.enums.WorkType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;

@ExtendWith(MockitoExtension.class)
class JobPostEntityTest {

  private static final String JOB_POST_ID = "33333333-3333-3333-3333-333333333333";
  private static final String TITLE = "Backend Engineer";
  private static final String DESCRIPTION = "Design and maintain backend services.";
  private static final WorkType WORK_TYPE = WorkType.REMOTE;
  private static final Integer SALARY_RANGE_LOWER = 80000;
  private static final Integer SALARY_RANGE_TOP = 120000;
  private static final String JOB_URL = "https://lynq.ai/jobs/backend-engineer";
  private static final JobPostSource JOB_POST_TYPE = JobPostSource.LYNQ;
  private static final LocalDate CREATED_ON = LocalDate.of(2026, 6, 25);

  @Mock
  private UserEntity createdByUser;

  @Mock
  private CompanyEntity company;

  private JobPostEntity jobPostEntity;

  @BeforeEach
  void setUp() {
    jobPostEntity = JobPostEntity.builder()
        .id(JOB_POST_ID)
        .title(TITLE)
        .description(DESCRIPTION)
        .workType(WORK_TYPE)
        .salaryRangeDown(SALARY_RANGE_LOWER)
        .salaryRangeTop(SALARY_RANGE_TOP)
        .jobUrl(JOB_URL)
        .jobPostSource(JOB_POST_TYPE)
        .createdByUser(createdByUser)
        .company(company)
        .createdOn(CREATED_ON)
        .build();
  }

  @Test
  void builderPopulatesAllScalarFields() {
    assertThat(jobPostEntity.getId(), is(JOB_POST_ID));
    assertThat(jobPostEntity.getTitle(), is(TITLE));
    assertThat(jobPostEntity.getDescription(), is(DESCRIPTION));
    assertThat(jobPostEntity.getWorkType(), is(WORK_TYPE));
    assertThat(jobPostEntity.getSalaryRangeDown(), is(SALARY_RANGE_LOWER));
    assertThat(jobPostEntity.getSalaryRangeTop(), is(SALARY_RANGE_TOP));
    assertThat(jobPostEntity.getJobUrl(), is(JOB_URL));
    assertThat(jobPostEntity.getJobPostSource(), is(JOB_POST_TYPE));
    assertThat(jobPostEntity.getCreatedOn(), is(CREATED_ON));
  }

  @Test
  void builderWiresAssociatedEntities() {
    assertThat(jobPostEntity.getCreatedByUser(), is(sameInstance(createdByUser)));
    assertThat(jobPostEntity.getCompany(), is(sameInstance(company)));
  }

  @Test
  void settersUpdateScalarFields() {
    JobPostEntity target = new JobPostEntity();

    target.setId(JOB_POST_ID);
    target.setTitle(TITLE);
    target.setWorkType(WORK_TYPE);
    target.setJobPostSource(JOB_POST_TYPE);
    target.setCreatedOn(CREATED_ON);

    assertThat(target.getId(), is(JOB_POST_ID));
    assertThat(target.getTitle(), is(TITLE));
    assertThat(target.getWorkType(), is(WORK_TYPE));
    assertThat(target.getJobPostSource(), is(JOB_POST_TYPE));
    assertThat(target.getCreatedOn(), is(CREATED_ON));
  }

  @Test
  void settersUpdateAssociatedEntities() {
    JobPostEntity target = new JobPostEntity();

    target.setCreatedByUser(createdByUser);
    target.setCompany(company);

    assertThat(target.getCreatedByUser(), is(sameInstance(createdByUser)));
    assertThat(target.getCompany(), is(sameInstance(company)));
  }
}