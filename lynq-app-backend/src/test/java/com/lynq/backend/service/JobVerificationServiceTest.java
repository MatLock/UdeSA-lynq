package com.lynq.backend.service;

import com.lynq.backend.config.VerificationProperties;
import com.lynq.backend.controller.request.LivenessReportRequest;
import com.lynq.backend.controller.response.ExpireJobPostsRestResponse;
import com.lynq.backend.controller.response.LivenessRestResponse;
import com.lynq.backend.controller.response.VerificationCandidateRestResponse;
import com.lynq.backend.enums.CloseReason;
import com.lynq.backend.enums.JobPostSource;
import com.lynq.backend.enums.JobStatus;
import com.lynq.backend.enums.LivenessOutcome;
import com.lynq.backend.event.DomainEvent;
import com.lynq.backend.event.DomainEventPublisher;
import com.lynq.backend.event.payload.JobPostClosedPayload;
import com.lynq.backend.model.JobPostEntity;
import com.lynq.backend.repository.JobPostRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobVerificationServiceTest {

  private static final LocalDate TODAY = LocalDate.now(ZoneOffset.UTC);
  private static final LocalDate LAST_SEEN = TODAY.minusDays(30);
  private static final String ALIVE_ID = "alive-job";
  private static final String CLOSED_ID = "closed-job";
  private static final String URL = "https://ar.computrabajo.com/ofertas-de-trabajo/oferta-1";

  @Mock
  private JobPostRepository jobPostRepository;

  @Mock
  private DomainEventPublisher domainEventPublisher;

  @Mock
  private VerificationProperties properties;

  private JobVerificationService service;

  @BeforeEach
  void setUp() {
    service = new JobVerificationService(jobPostRepository, domainEventPublisher, properties);
  }

  private JobPostEntity openExternal(String id) {
    return JobPostEntity.builder()
        .id(id)
        .jobPostSource(JobPostSource.COMPUTRABAJO)
        .jobStatus(JobStatus.OPEN)
        .jobUrl(URL)
        .category("TECNOLOGIA")
        .createdOn(LAST_SEEN)
        .lastSeenOn(LAST_SEEN)
        .totalSeen(0L)
        .build();
  }

  private LivenessReportRequest report(String id, LivenessOutcome outcome) {
    LivenessReportRequest report = mock(LivenessReportRequest.class);
    when(report.getId()).thenReturn(id);
    when(report.getOutcome()).thenReturn(outcome);
    return report;
  }

  private LivenessReportRequest skippedReport(String id) {
    LivenessReportRequest report = mock(LivenessReportRequest.class);
    when(report.getId()).thenReturn(id);
    return report;
  }

  @Test
  void candidatesAskTheRepositoryForTheWindowTheQuotaAndTodayAndKeepItsOrder() {
    when(properties.windowDays()).thenReturn(20);
    when(properties.quotaPerCategory()).thenReturn(10);
    when(jobPostRepository.findVerificationCandidateIds(TODAY.minusDays(20), TODAY, 10))
        .thenReturn(List.of("second", "first"));
    when(jobPostRepository.findAllById(List.of("second", "first")))
        .thenReturn(List.of(openExternal("first"), openExternal("second")));

    List<VerificationCandidateRestResponse> candidates = service.candidates().getCandidates();

    assertThat(candidates.stream().map(VerificationCandidateRestResponse::getId).toList(),
        contains("second", "first"));
    assertThat(candidates.getFirst().getJobUrl(), is(URL));
    assertThat(candidates.getFirst().getSource(), is(JobPostSource.COMPUTRABAJO));
    assertThat(candidates.getFirst().getCategory(), is("TECNOLOGIA"));
    assertThat(candidates.getFirst().getLastSeenOn(), is(LAST_SEEN));
  }

  @Test
  void noCandidatesLoadNoJobPosts() {
    when(properties.windowDays()).thenReturn(20);
    when(properties.quotaPerCategory()).thenReturn(10);
    when(jobPostRepository.findVerificationCandidateIds(any(), any(), any(Integer.class)))
        .thenReturn(List.of());

    assertThat(service.candidates().getCandidates(), is(empty()));
    verify(jobPostRepository, never()).findAllById(anyList());
  }

  @Test
  void anAliveReportRenewsWhenThePostWasSeenAndChecked() {
    JobPostEntity job = openExternal(ALIVE_ID);
    when(jobPostRepository.findAllById(List.of(ALIVE_ID))).thenReturn(List.of(job));

    LivenessRestResponse response = service.report(List.of(report(ALIVE_ID, LivenessOutcome.ALIVE)));

    assertThat(response.getAlive(), is(1));
    assertThat(job.getLastSeenOn(), is(TODAY));
    assertThat(job.getLastCheckedOn(), is(TODAY));
    assertThat(job.getJobStatus(), is(JobStatus.OPEN));
    verify(jobPostRepository).save(job);
    verify(domainEventPublisher, never()).publish(any());
  }

  @Test
  void aClosedReportClosesThePostTodayAsVerifiedClosedAndPublishesIt() {
    JobPostEntity job = openExternal(CLOSED_ID);
    when(jobPostRepository.findAllById(List.of(CLOSED_ID))).thenReturn(List.of(job));

    LivenessRestResponse response =
        service.report(List.of(report(CLOSED_ID, LivenessOutcome.CLOSED)));

    assertThat(response.getClosed(), is(1));
    assertThat(job.getJobStatus(), is(JobStatus.CLOSE));
    assertThat(job.getClosedOn(), is(TODAY));
    assertThat(job.getCloseReason(), is(CloseReason.VERIFIED_CLOSED));
    assertThat(job.getLastCheckedOn(), is(TODAY));
    assertThat(job.getLastSeenOn(), is(LAST_SEEN));
    ArgumentCaptor<DomainEvent> captor = ArgumentCaptor.forClass(DomainEvent.class);
    verify(domainEventPublisher).publish(captor.capture());
    assertThat(captor.getValue().eventType(), is("JobPostClosed"));
    JobPostClosedPayload payload = (JobPostClosedPayload) captor.getValue().payload();
    assertThat(payload.closedOn(), is(TODAY));
    assertThat(payload.closeReason(), is("VERIFIED_CLOSED"));
  }

  @Test
  void aGoneReportClosesThePostAsVerifiedGone() {
    JobPostEntity job = openExternal(CLOSED_ID);
    when(jobPostRepository.findAllById(List.of(CLOSED_ID))).thenReturn(List.of(job));

    LivenessRestResponse response = service.report(List.of(report(CLOSED_ID, LivenessOutcome.GONE)));

    assertThat(response.getGone(), is(1));
    assertThat(job.getCloseReason(), is(CloseReason.VERIFIED_GONE));
    verify(domainEventPublisher).publish(any());
  }

  @Test
  void anUnknownReportOnlyStampsTheCheck() {
    JobPostEntity job = openExternal(ALIVE_ID);
    when(jobPostRepository.findAllById(List.of(ALIVE_ID))).thenReturn(List.of(job));

    LivenessRestResponse response =
        service.report(List.of(report(ALIVE_ID, LivenessOutcome.UNKNOWN)));

    assertThat(response.getUnknown(), is(1));
    assertThat(job.getLastCheckedOn(), is(TODAY));
    assertThat(job.getLastSeenOn(), is(LAST_SEEN));
    assertThat(job.getJobStatus(), is(JobStatus.OPEN));
    verify(domainEventPublisher, never()).publish(any());
  }

  @Test
  void reportsForUnknownLynqOrClosedPostsAreSkipped() {
    JobPostEntity lynq = openExternal("lynq-job");
    lynq.setJobPostSource(JobPostSource.LYNQ);
    JobPostEntity closed = openExternal(CLOSED_ID);
    closed.setJobStatus(JobStatus.CLOSE);
    when(jobPostRepository.findAllById(List.of("lynq-job", CLOSED_ID, "missing")))
        .thenReturn(List.of(lynq, closed));

    LivenessRestResponse response = service.report(List.of(
        skippedReport("lynq-job"), skippedReport(CLOSED_ID), skippedReport("missing")));

    assertThat(response.getSkipped(), is(3));
    assertThat(response.getAlive() + response.getClosed() + response.getGone()
        + response.getUnknown(), is(0));
    verify(jobPostRepository, never()).save(any(JobPostEntity.class));
    verify(domainEventPublisher, never()).publish(any());
  }

  @Test
  void aSecondReportForAPostItJustClosedIsSkipped() {
    JobPostEntity job = openExternal(CLOSED_ID);
    when(jobPostRepository.findAllById(List.of(CLOSED_ID))).thenReturn(List.of(job));

    LivenessRestResponse response = service.report(List.of(
        report(CLOSED_ID, LivenessOutcome.GONE), skippedReport(CLOSED_ID)));

    assertThat(response.getGone(), is(1));
    assertThat(response.getSkipped(), is(1));
    verify(domainEventPublisher, times(1)).publish(any());
  }

  @Test
  void expireClosesEveryStalePostAsExpiredByPolicyAndPublishesEachClose() {
    JobPostEntity first = openExternal("first");
    JobPostEntity second = openExternal("second");
    second.setJobUrl(null);
    when(properties.expireAfterDays()).thenReturn(25);
    when(jobPostRepository.findOpenExternalNotSeenSince(TODAY.minusDays(25)))
        .thenReturn(List.of(first, second));

    ExpireJobPostsRestResponse response = service.expire();

    assertThat(response.getExpired(), is(2));
    assertThat(first.getJobStatus(), is(JobStatus.CLOSE));
    assertThat(first.getClosedOn(), is(TODAY));
    assertThat(first.getCloseReason(), is(CloseReason.EXPIRED_BY_POLICY));
    assertThat(second.getCloseReason(), is(CloseReason.EXPIRED_BY_POLICY));
    verify(jobPostRepository).saveAll(List.of(first, second));
    verify(domainEventPublisher, times(2)).publish(any());
  }

  @Test
  void expireWithNothingStaleClosesNothing() {
    when(properties.expireAfterDays()).thenReturn(25);
    when(jobPostRepository.findOpenExternalNotSeenSince(TODAY.minusDays(25))).thenReturn(List.of());

    assertThat(service.expire().getExpired(), is(0));
    verify(domainEventPublisher, never()).publish(any());
  }
}
