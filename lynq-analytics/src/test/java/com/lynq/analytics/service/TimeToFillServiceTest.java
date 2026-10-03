package com.lynq.analytics.service;

import com.lynq.analytics.config.TimeToFillProperties;
import com.lynq.analytics.enums.JobStatus;
import com.lynq.analytics.exceptions.ForbiddenException;
import com.lynq.analytics.exceptions.NotFoundException;
import com.lynq.analytics.model.JobPostEntity;
import com.lynq.analytics.repository.JobPostRepository;
import com.lynq.analytics.similarity.SimilarMatch;
import com.lynq.analytics.similarity.SimilarMatches;
import com.lynq.analytics.stats.TimeToFill;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TimeToFillServiceTest {

  private static final String JOB_ID = "77777777-7777-7777-7777-777777777777";
  private static final String OWNER_ID = "11111111-1111-1111-1111-111111111111";
  private static final ZoneId ZONE = ZoneId.of("America/Argentina/Buenos_Aires");
  private static final LocalDate TODAY = LocalDate.parse("2026-10-03");
  private static final LocalDate PUBLISHED_ON = LocalDate.parse("2026-08-01");

  @Mock
  private JobPostRepository jobPostRepository;

  @Mock
  private SimilarityService similarityService;

  @Mock
  private TimeToFillProperties properties;

  @Mock
  private Clock clock;

  private TimeToFillService service;

  @BeforeEach
  void setUp() {
    service = new TimeToFillService(jobPostRepository, similarityService, properties, clock);
  }

  @Test
  void summarisesTheDaysSimilarClosedPostsStayedOpenLeavingTheExpiredOnesOut() {
    givenOpenReference();
    givenProperties();
    givenSimilar(
        closed("A", "COMPUTRABAJO", 10, "VERIFIED_GONE"),
        closed("B", "BUMERAN", 14, "VERIFIED_CLOSED"),
        closed("C", "LYNQ", 21, "OWNER"),
        closed("D", "BUMERAN", 25, "VERIFIED_GONE"),
        closed("E", "COMPUTRABAJO", 30, "VERIFIED_GONE"),
        closed("F", "BUMERAN", 40, "EXPIRED_BY_POLICY"),
        closed("G", "COMPUTRABAJO", 45, "EXPIRED_BY_POLICY"));

    TimeToFill timeToFill = service.timeToFill(JOB_ID, OWNER_ID);

    assertThat(timeToFill.n(), is(5));
    assertThat(timeToFill.median(), is(21.0));
    assertThat(timeToFill.p25(), is(14.0));
    assertThat(timeToFill.p75(), is(25.0));
    assertThat(timeToFill.insufficientData(), is(false));
    assertThat(timeToFill.externalJobPosts(), is(4));
    assertThat(timeToFill.expiredByPolicy(), is(2));
    assertThat(timeToFill.expiredAfterDays(), is(25));
    assertThat(timeToFill.overall(), is(nullValue()));
    verify(jobPostRepository, never()).findByStatusAndClosedOnIsNotNull(any());
  }

  @Test
  void asksOnlyForSimilarPostsThatClosedOnADate() {
    givenOpenReference();
    givenProperties();
    ArgumentCaptor<Predicate<JobPostEntity>> eligible = givenSimilar();
    givenOverall();

    service.timeToFill(JOB_ID, OWNER_ID);

    assertThat(eligible.getValue().test(closed("A", "BUMERAN", 10, "VERIFIED_GONE")), is(true));
    assertThat(eligible.getValue().test(closed("B", "BUMERAN", 10, "EXPIRED_BY_POLICY")),
        is(true));
    JobPostEntity open = closed("C", "BUMERAN", 10, null);
    open.setStatus(JobStatus.OPEN);
    open.setClosedOn(null);
    assertThat(eligible.getValue().test(open), is(false));
    JobPostEntity undated = closed("D", "BUMERAN", 10, "OWNER");
    undated.setClosedOn(null);
    assertThat(eligible.getValue().test(undated), is(false));
  }

  @Test
  void countsAReopenedPostFromItsLatestReopening() {
    givenOpenReference();
    givenProperties();
    JobPostEntity reopened = closed("A", "LYNQ", 60, "OWNER");
    reopened.setReopenedOn(PUBLISHED_ON.plusDays(50));
    givenSimilar(reopened, closed("B", "LYNQ", 10, "OWNER"), closed("C", "LYNQ", 10, "OWNER"),
        closed("D", "LYNQ", 10, "OWNER"), closed("E", "LYNQ", 10, "OWNER"));

    TimeToFill timeToFill = service.timeToFill(JOB_ID, OWNER_ID);

    assertThat(timeToFill.median(), is(10.0));
    assertThat(timeToFill.externalJobPosts(), is(0));
  }

  @Test
  void leavesOutAPostThatClosedBeforeItOpened() {
    givenOpenReference();
    givenProperties();
    JobPostEntity broken = closed("A", "BUMERAN", 5, "VERIFIED_GONE");
    broken.setClosedOn(PUBLISHED_ON.minusDays(3));
    givenSimilar(broken);
    givenOverall();

    TimeToFill timeToFill = service.timeToFill(JOB_ID, OWNER_ID);

    assertThat(timeToFill.n(), is(0));
    assertThat(timeToFill.externalJobPosts(), is(0));
  }

  @Test
  void withholdsTheFiguresBelowFiveAndFallsBackToEveryClosedPostMarkedAsSuch() {
    givenOpenReference();
    givenProperties();
    givenSimilar(closed("A", "BUMERAN", 10, "VERIFIED_GONE"),
        closed("B", "BUMERAN", 50, "EXPIRED_BY_POLICY"));
    JobPostEntity reference = closed(JOB_ID, "LYNQ", 99, "OWNER");
    givenOverall(reference,
        closed("A", "BUMERAN", 10, "VERIFIED_GONE"),
        closed("X1", "LYNQ", 20, "OWNER"),
        closed("X2", "COMPUTRABAJO", 30, "VERIFIED_CLOSED"),
        closed("X3", "BUMERAN", 40, "VERIFIED_GONE"),
        closed("X4", "BUMERAN", 50, "VERIFIED_GONE"),
        closed("X5", "BUMERAN", 60, "EXPIRED_BY_POLICY"));

    TimeToFill timeToFill = service.timeToFill(JOB_ID, OWNER_ID);

    assertThat(timeToFill.insufficientData(), is(true));
    assertThat(timeToFill.n(), is(1));
    assertThat(timeToFill.median(), is(nullValue()));
    assertThat(timeToFill.expiredByPolicy(), is(1));
    assertThat(timeToFill.overall().n(), is(5));
    assertThat(timeToFill.overall().median(), is(30.0));
    assertThat(timeToFill.overall().insufficientData(), is(false));
  }

  @Test
  void countsTheDaysThisPostHasBeenOpenUntilToday() {
    givenOpenReference();
    givenProperties();
    givenSimilar();
    givenOverall();

    assertThat(service.timeToFill(JOB_ID, OWNER_ID).daysOpen(), is(63L));
  }

  @Test
  void countsTheDaysAClosedPostStayedOpen() {
    JobPostEntity reference = closed(JOB_ID, "LYNQ", 12, "OWNER");
    reference.setCreatedByUserId(OWNER_ID);
    when(jobPostRepository.findById(JOB_ID)).thenReturn(Optional.of(reference));
    givenProperties();
    givenSimilar();
    givenOverall();

    assertThat(service.timeToFill(JOB_ID, OWNER_ID).daysOpen(), is(12L));
  }

  @Test
  void refusesACompanyThatDidNotPublishTheJobPost() {
    givenOpenReference();

    ForbiddenException exception = assertThrows(ForbiddenException.class,
        () -> service.timeToFill(JOB_ID, "someone-else"));

    assertThat(exception.getMessage(),
        is("Only the company that published the job post can read its time to fill"));
    verifyNoInteractions(similarityService);
  }

  @Test
  void refusesEveryoneForAScrapedJobPost() {
    JobPostEntity scraped = closed(JOB_ID, "BUMERAN", 3, null);
    scraped.setStatus(JobStatus.OPEN);
    when(jobPostRepository.findById(JOB_ID)).thenReturn(Optional.of(scraped));

    assertThrows(ForbiddenException.class, () -> service.timeToFill(JOB_ID, OWNER_ID));
  }

  @Test
  void answersNotFoundForAJobPostAnalyticsDoesNotHold() {
    when(jobPostRepository.findById(JOB_ID)).thenReturn(Optional.empty());

    NotFoundException exception = assertThrows(NotFoundException.class,
        () -> service.timeToFill(JOB_ID, OWNER_ID));

    assertThat(exception.getMessage(), is("Job post '" + JOB_ID + "' not found"));
  }

  private void givenOpenReference() {
    JobPostEntity reference = JobPostEntity.builder()
        .id(JOB_ID)
        .source("LYNQ")
        .status(JobStatus.OPEN)
        .createdByUserId(OWNER_ID)
        .publishedOn(PUBLISHED_ON)
        .build();
    when(jobPostRepository.findById(JOB_ID)).thenReturn(Optional.of(reference));
  }

  private void givenProperties() {
    when(properties.minSample()).thenReturn(5);
    when(properties.expiredAfterDays()).thenReturn(25);
    lenient().when(clock.instant())
        .thenReturn(TODAY.atStartOfDay(ZONE).plusHours(10).toInstant());
    lenient().when(clock.getZone()).thenReturn(ZONE);
  }

  private ArgumentCaptor<Predicate<JobPostEntity>> givenSimilar(JobPostEntity... posts) {
    @SuppressWarnings("unchecked")
    ArgumentCaptor<Predicate<JobPostEntity>> eligible = ArgumentCaptor.forClass(Predicate.class);
    when(similarityService.findSimilarJobPosts(eq(JOB_ID), eligible.capture()))
        .thenReturn(new SimilarMatches<>(List.of(posts).stream()
            .map(post -> new SimilarMatch<>(post, 1.0, 0))
            .toList(), 0.5, false));
    return eligible;
  }

  private void givenOverall(JobPostEntity... posts) {
    when(jobPostRepository.findByStatusAndClosedOnIsNotNull(JobStatus.CLOSE))
        .thenReturn(List.of(posts));
  }

  private static JobPostEntity closed(String id, String source, int daysOpen, String reason) {
    return JobPostEntity.builder()
        .id(id)
        .source(source)
        .status(JobStatus.CLOSE)
        .publishedOn(PUBLISHED_ON)
        .closedOn(PUBLISHED_ON.plusDays(daysOpen))
        .closeReason(reason)
        .build();
  }
}
