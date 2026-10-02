package com.lynq.backend.service;

import com.lynq.backend.controller.response.ReplayEventsRestResponse;
import com.lynq.backend.enums.CloseReason;
import com.lynq.backend.enums.JobPostSource;
import com.lynq.backend.enums.JobStatus;
import com.lynq.backend.enums.WorkType;
import com.lynq.backend.event.DomainEvent;
import com.lynq.backend.event.SnsDomainEventSender;
import com.lynq.backend.event.payload.ApplicationSubmittedPayload;
import com.lynq.backend.event.payload.CandidateExpectedSalaryUpdatedPayload;
import com.lynq.backend.model.JobPostEntity;
import com.lynq.backend.model.JobPostSkillEntity;
import com.lynq.backend.model.UserApplicationJobEntity;
import com.lynq.backend.model.UserEntity;
import com.lynq.backend.model.UserResumeEntity;
import com.lynq.backend.model.UserSkillsEntity;
import com.lynq.backend.repository.JobPostRepository;
import com.lynq.backend.repository.UserApplicationJobRepository;
import com.lynq.backend.repository.UserRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
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
import org.springframework.data.domain.Sort;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.aMapWithSize;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DomainEventReplayServiceTest {

  private static final LocalDate CREATED_ON = LocalDate.of(2026, Month.SEPTEMBER, 1);
  private static final LocalDate CLOSED_ON = LocalDate.of(2026, Month.SEPTEMBER, 21);
  private static final LocalDate RESUME_ON = LocalDate.of(2026, Month.SEPTEMBER, 10);
  private static final LocalDate APPLIED_ON = LocalDate.of(2026, Month.SEPTEMBER, 12);

  @Mock
  private JobPostRepository jobPostRepository;

  @Mock
  private UserRepository userRepository;

  @Mock
  private UserApplicationJobRepository userApplicationJobRepository;

  @Mock
  private SnsDomainEventSender sender;

  private DomainEventReplayService service;

  @BeforeEach
  void setUp() {
    service = new DomainEventReplayService(jobPostRepository, userRepository,
        userApplicationJobRepository, sender);
    lenient().when(jobPostRepository.findAll(any(Pageable.class))).thenReturn(Page.empty());
    lenient().when(userRepository.findAll(any(Pageable.class))).thenReturn(Page.empty());
    lenient().when(userApplicationJobRepository.findAll(any(Pageable.class)))
        .thenReturn(Page.empty());
    lenient().when(sender.sendAll(anyList())).thenReturn(List.of());
  }

  @Test
  void anOpenJobPostIsPublishedOnTheDayItWasCreated() {
    givenJobPosts(job("open", JobStatus.OPEN, null));

    service.replay();

    List<DomainEvent> events = sentEvents();
    assertThat(events.stream().map(DomainEvent::eventType).toList(),
        contains("JobPostPublished"));
    assertThat(events.getFirst().occurredOn(), is(Instant.parse("2026-09-01T00:00:00Z")));
  }

  @Test
  void aClosedJobPostIsPublishedAndThenClosedAtTheEndOfItsCloseDay() {
    givenJobPosts(job("closed", JobStatus.CLOSE, CLOSED_ON));

    service.replay();

    List<DomainEvent> events = sentEvents();
    assertThat(events.stream().map(DomainEvent::eventType).toList(),
        contains("JobPostPublished", "JobPostClosed"));
    assertThat(events.get(1).occurredOn(), is(Instant.parse("2026-09-21T23:59:59.999Z")));
  }

  @Test
  void aJobPostClosedTheSameDayItOpenedIsClosedAfterItIsPublished() {
    givenJobPosts(job("same-day", JobStatus.CLOSE, CREATED_ON));

    service.replay();

    List<DomainEvent> events = sentEvents();
    assertThat(events.get(1).occurredOn().isAfter(events.get(0).occurredOn()), is(true));
  }

  @Test
  void aClosedJobPostWithoutACloseDateIsSkipped() {
    givenJobPosts(job("legacy", JobStatus.CLOSE, null), job("open", JobStatus.OPEN, null));

    ReplayEventsRestResponse response = service.replay();

    assertThat(response.getSkippedJobPosts(), is(1));
    assertThat(sentEvents().stream().map(DomainEvent::aggregateId).toList(), contains("open"));
  }

  @Test
  void aCandidateIsReplayedWithItsSkillsAsOfItsLatestResumeAndItsSalary() {
    UserEntity candidate = candidate();
    candidate.setExpectedSalary(2000000);
    candidate.setExpectedSalaryCurrency("ARS");
    when(userRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(candidate)));

    service.replay();

    List<DomainEvent> events = sentEvents();
    assertThat(events.stream().map(DomainEvent::eventType).toList(),
        contains("CandidateSkillsUpdated", "CandidateExpectedSalaryUpdated"));
    assertThat(events.get(0).occurredOn(), is(Instant.parse("2026-09-10T00:00:00Z")));
    assertThat(events.get(1).occurredOn(), is(Instant.parse("2026-09-01T00:00:00Z")));
    assertThat(((CandidateExpectedSalaryUpdatedPayload) events.get(1).payload()).expectedSalary(),
        is(2000000));
  }

  @Test
  void aCandidateWithoutSkillsOrSalaryIsNotReplayed() {
    UserEntity company = UserEntity.builder().id("company-user").createdOn(CREATED_ON).build();
    when(userRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(company)));

    ReplayEventsRestResponse response = service.replay();

    assertThat(response.getPublished(), is(0));
  }

  @Test
  void anApplicationIsReplayedWithItsDateAndTheCurrentScore() {
    JobPostEntity job = job("job", JobStatus.OPEN, null);
    UserEntity candidate = candidate();
    UserApplicationJobEntity application = UserApplicationJobEntity.builder()
        .id("application")
        .jobPost(job)
        .user(candidate)
        .appliedOn(APPLIED_ON)
        .build();
    when(userApplicationJobRepository.findAll(any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(application)));

    service.replay();

    DomainEvent event = sentEvents().getFirst();
    assertThat(event.eventType(), is("ApplicationSubmitted"));
    assertThat(event.occurredOn(), is(Instant.parse("2026-09-12T00:00:00Z")));
    assertThat(((ApplicationSubmittedPayload) event.payload()).lynqScore(),
        is(JobService.scoreOf(job, candidate)));
  }

  @Test
  void jobPostsAreReplayedBeforeCandidatesAndCandidatesBeforeApplications() {
    givenJobPosts(job("job", JobStatus.OPEN, null));
    when(userRepository.findAll(any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(candidate())));
    when(userApplicationJobRepository.findAll(any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(UserApplicationJobEntity.builder()
            .id("application")
            .jobPost(job("job", JobStatus.OPEN, null))
            .user(candidate())
            .appliedOn(APPLIED_ON)
            .build())));

    service.replay();

    assertThat(sentEvents().stream().map(DomainEvent::eventType).toList(),
        contains("JobPostPublished", "CandidateSkillsUpdated", "ApplicationSubmitted"));
  }

  @Test
  void replayingTwiceSendsTheSameEventIds() {
    givenJobPosts(job("closed", JobStatus.CLOSE, CLOSED_ON));
    when(userRepository.findAll(any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(candidate())));

    service.replay();
    service.replay();

    ArgumentCaptor<List<DomainEvent>> captor = sentBatches(6);
    List<UUID> first = idsOf(captor.getAllValues().subList(0, 3));
    List<UUID> second = idsOf(captor.getAllValues().subList(3, 6));
    assertThat(second, is(first));
  }

  @Test
  void everyPageOfEveryTableIsReplayed() {
    List<JobPostEntity> firstPage = IntStream.range(0, DomainEventReplayService.PAGE_SIZE)
        .mapToObj(i -> job("job-" + i, JobStatus.OPEN, null))
        .toList();
    Pageable first = PageRequest.of(0, DomainEventReplayService.PAGE_SIZE, Sort.by("id"));
    when(jobPostRepository.findAll(first))
        .thenReturn(new PageImpl<>(firstPage, first, DomainEventReplayService.PAGE_SIZE + 1L));
    when(jobPostRepository.findAll(first.next()))
        .thenReturn(new PageImpl<>(List.of(job("last", JobStatus.OPEN, null)), first.next(),
            DomainEventReplayService.PAGE_SIZE + 1L));

    ReplayEventsRestResponse response = service.replay();

    assertThat(response.getPublished(), is(DomainEventReplayService.PAGE_SIZE + 1));
  }

  @Test
  void theResponseCountsWhatWasPublishedByTypeAndWhatFailed() {
    givenJobPosts(job("open", JobStatus.OPEN, null), job("closed", JobStatus.CLOSE, CLOSED_ON));
    when(sender.sendAll(anyList())).thenAnswer(invocation -> {
      List<DomainEvent> batch = invocation.getArgument(0);
      return batch.isEmpty() ? List.of() : List.of(batch.getLast());
    });

    ReplayEventsRestResponse response = service.replay();

    assertThat(response.getPublished(), is(2));
    assertThat(response.getFailed(), is(1));
    assertThat(response.getPublishedByEventType(), aMapWithSize(1));
    assertThat(response.getPublishedByEventType(), hasEntry("JobPostPublished", 2));
  }

  private void givenJobPosts(JobPostEntity... jobs) {
    when(jobPostRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(jobs)));
  }

  private List<DomainEvent> sentEvents() {
    return sentBatches(3).getAllValues().stream().flatMap(List::stream).toList();
  }

  @SuppressWarnings("unchecked")
  private ArgumentCaptor<List<DomainEvent>> sentBatches(int calls) {
    ArgumentCaptor<List<DomainEvent>> captor = ArgumentCaptor.forClass(List.class);
    verify(sender, times(calls)).sendAll(captor.capture());
    return captor;
  }

  private static List<UUID> idsOf(List<List<DomainEvent>> batches) {
    return batches.stream().flatMap(List::stream).map(DomainEvent::eventId).toList();
  }

  private static JobPostEntity job(String id, JobStatus status, LocalDate closedOn) {
    JobPostEntity job = JobPostEntity.builder()
        .id(id)
        .title("Backend Developer")
        .workType(WorkType.REMOTE)
        .jobPostSource(JobPostSource.LYNQ)
        .createdOn(CREATED_ON)
        .jobStatus(status)
        .closedOn(closedOn)
        .closeReason(closedOn == null ? null : CloseReason.OWNER)
        .skills(new ArrayList<>())
        .similarityTags(new ArrayList<>())
        .build();
    job.getSkills().add(JobPostSkillEntity.builder().skill("Java").jobPost(job).build());
    job.getSkills().add(JobPostSkillEntity.builder().skill("Spring").jobPost(job).build());
    return job;
  }

  private static UserEntity candidate() {
    UserEntity user = UserEntity.builder()
        .id("candidate")
        .createdOn(CREATED_ON)
        .skills(new ArrayList<>())
        .similarityTags(new ArrayList<>())
        .resumes(new ArrayList<>())
        .build();
    user.getSkills().add(UserSkillsEntity.builder().skill("Java").user(user).build());
    user.getResumes().add(UserResumeEntity.builder().createdOn(CREATED_ON.plusDays(2)).build());
    user.getResumes().add(UserResumeEntity.builder().createdOn(RESUME_ON).build());
    return user;
  }
}
