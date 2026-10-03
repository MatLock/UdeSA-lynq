package com.lynq.analytics.service;

import com.lynq.analytics.config.StandingProperties;
import com.lynq.analytics.enums.JobStatus;
import com.lynq.analytics.model.JobPostEntity;
import com.lynq.analytics.repository.ApplicationRepository;
import com.lynq.analytics.repository.ApplicationRepository.JobScore;
import com.lynq.analytics.repository.JobPostRepository;
import com.lynq.analytics.stats.CompanyJobs;
import com.lynq.analytics.stats.CompanyJobs.CompanyJob;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompanyJobsServiceTest {

  private static final String USER_ID = "22222222-2222-2222-2222-222222222222";

  @Mock
  private JobPostRepository jobPostRepository;

  @Mock
  private ApplicationRepository applicationRepository;

  private CompanyJobsService companyJobsService;

  @BeforeEach
  void setUp() {
    companyJobsService = new CompanyJobsService(jobPostRepository, applicationRepository,
        new StandingProperties(5));
  }

  @Test
  void comparesTheApplicationsAndTheMedianScoreOfEachJobPost() {
    when(jobPostRepository.findByCreatedByUserIdOrderByPublishedOnDescIdAsc(USER_ID))
        .thenReturn(List.of(jobPost("busy", JobStatus.OPEN), jobPost("quiet", JobStatus.CLOSE),
            jobPost("empty", JobStatus.OPEN)));
    when(applicationRepository.findScoresByJobIds(List.of("busy", "quiet", "empty")))
        .thenReturn(List.of(score("busy", 40), score("busy", 50), score("busy", 60),
            score("busy", 70), score("busy", 90), score("quiet", 80)));

    CompanyJobs jobs = companyJobsService.jobs(USER_ID);

    assertThat(jobs.jobs().stream().map(CompanyJob::jobId).toList(),
        contains("busy", "quiet", "empty"));
    CompanyJob busy = jobs.jobs().get(0);
    assertThat(busy.applications(), is(5));
    assertThat(busy.medianScore(), is(60.0));
    assertThat(busy.insufficientData(), is(false));
    assertThat(busy.status(), is("OPEN"));
    CompanyJob quiet = jobs.jobs().get(1);
    assertThat(quiet.applications(), is(1));
    assertThat(quiet.medianScore(), is(nullValue()));
    assertThat(quiet.insufficientData(), is(true));
    assertThat(quiet.status(), is("CLOSE"));
    assertThat(jobs.jobs().get(2).applications(), is(0));
  }

  @Test
  void answersAnEmptyListToACompanyWithoutJobPosts() {
    when(jobPostRepository.findByCreatedByUserIdOrderByPublishedOnDescIdAsc(USER_ID))
        .thenReturn(List.of());

    assertThat(companyJobsService.jobs(USER_ID).jobs(), is(empty()));
    verify(applicationRepository, never()).findScoresByJobIds(any());
  }

  private static JobPostEntity jobPost(String id, JobStatus status) {
    return JobPostEntity.builder()
        .id(id)
        .title("Title " + id)
        .workType("REMOTE")
        .source("LYNQ")
        .status(status)
        .publishedOn(LocalDate.parse("2026-09-20"))
        .createdByUserId(USER_ID)
        .build();
  }

  private static JobScore score(String jobId, int score) {
    return new JobScore() {
      @Override
      public String getJobId() {
        return jobId;
      }

      @Override
      public int getLynqScore() {
        return score;
      }
    };
  }
}
