package com.lynq.analytics.service;

import com.lynq.analytics.config.StandingProperties;
import com.lynq.analytics.exceptions.ForbiddenException;
import com.lynq.analytics.exceptions.NotFoundException;
import com.lynq.analytics.model.ApplicationEntity;
import com.lynq.analytics.repository.ApplicationRepository;
import com.lynq.analytics.repository.JobPostRepository;
import com.lynq.analytics.stats.Standing;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StandingServiceTest {

  private static final String JOB_ID = "77777777-7777-7777-7777-777777777777";
  private static final String USER_ID = "11111111-1111-1111-1111-111111111111";

  @Mock
  private ApplicationRepository applicationRepository;

  @Mock
  private JobPostRepository jobPostRepository;

  private StandingService standingService;

  @BeforeEach
  void setUp() {
    standingService = new StandingService(applicationRepository, jobPostRepository,
        new StandingProperties(5));
  }

  @Test
  void ranksTheCallersApplicationAmongTheScoresOfTheJobPost() {
    when(applicationRepository.findByJobIdAndCandidateId(JOB_ID, USER_ID))
        .thenReturn(Optional.of(application(72)));
    when(applicationRepository.findScoresByJobId(JOB_ID))
        .thenReturn(List.of(90, 72, 72, 60, 40, 30));

    Standing standing = standingService.standing(JOB_ID, USER_ID);

    assertThat(standing.rank(), is(2));
    assertThat(standing.totalApplicants(), is(6));
    assertThat(standing.score(), is(72));
    assertThat(standing.medianScore(), is(66.0));
    verify(jobPostRepository, never()).existsById(JOB_ID);
  }

  @Test
  void refusesACandidateWhoDidNotApplyToAKnownJobPost() {
    when(applicationRepository.findByJobIdAndCandidateId(JOB_ID, USER_ID))
        .thenReturn(Optional.empty());
    when(jobPostRepository.existsById(JOB_ID)).thenReturn(true);

    ForbiddenException ex = assertThrows(ForbiddenException.class,
        () -> standingService.standing(JOB_ID, USER_ID));

    assertThat(ex.getMessage(),
        is("Only a candidate who applied to the job post can read their standing"));
    verify(applicationRepository, never()).findScoresByJobId(JOB_ID);
  }

  @Test
  void failsWithNotFoundForAnUnknownJobPost() {
    when(applicationRepository.findByJobIdAndCandidateId(JOB_ID, USER_ID))
        .thenReturn(Optional.empty());
    when(jobPostRepository.existsById(JOB_ID)).thenReturn(false);

    NotFoundException ex = assertThrows(NotFoundException.class,
        () -> standingService.standing(JOB_ID, USER_ID));

    assertThat(ex.getMessage(), is("Job post '" + JOB_ID + "' not found"));
  }

  private static ApplicationEntity application(int score) {
    return ApplicationEntity.builder()
        .id("33333333-3333-3333-3333-333333333333")
        .jobId(JOB_ID)
        .candidateId(USER_ID)
        .lynqScore(score)
        .build();
  }
}
