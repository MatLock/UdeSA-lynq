package com.lynq.backend.service;

import com.lynq.backend.controller.response.ReplayEventsRestResponse;
import com.lynq.backend.enums.JobStatus;
import com.lynq.backend.event.DomainEvent;
import com.lynq.backend.event.DomainEvents;
import com.lynq.backend.event.SnsDomainEventSender;
import com.lynq.backend.model.JobPostEntity;
import com.lynq.backend.model.UserApplicationJobEntity;
import com.lynq.backend.model.UserEntity;
import com.lynq.backend.model.UserResumeEntity;
import com.lynq.backend.repository.JobPostRepository;
import com.lynq.backend.repository.UserApplicationJobRepository;
import com.lynq.backend.repository.UserRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Log4j2
public class DomainEventReplayService {

  static final int PAGE_SIZE = 200;

  private final JobPostRepository jobPostRepository;
  private final UserRepository userRepository;
  private final UserApplicationJobRepository userApplicationJobRepository;
  private final SnsDomainEventSender sender;

  public DomainEventReplayService(JobPostRepository jobPostRepository,
      UserRepository userRepository, UserApplicationJobRepository userApplicationJobRepository,
      SnsDomainEventSender sender) {
    this.jobPostRepository = jobPostRepository;
    this.userRepository = userRepository;
    this.userApplicationJobRepository = userApplicationJobRepository;
    this.sender = sender;
  }

  @Transactional(readOnly = true)
  public ReplayEventsRestResponse replay() {
    Tally tally = new Tally();
    replayEach(jobPostRepository, job -> jobPostEvents(job, tally), tally);
    replayEach(userRepository, DomainEventReplayService::candidateEvents, tally);
    replayEach(userApplicationJobRepository, application -> List.of(
        DomainEvents.applicationSubmitted(application,
            JobService.scoreOf(application.getJobPost(), application.getUser()),
            startOf(application.getAppliedOn()))), tally);

    log.info("message= Replayed {} domain events, {} could not be published, {} job posts "
            + "skipped: {}", tally.published, tally.failed, tally.skippedJobPosts,
        tally.publishedByEventType);
    return ReplayEventsRestResponse.builder()
        .published(tally.published)
        .failed(tally.failed)
        .skippedJobPosts(tally.skippedJobPosts)
        .publishedByEventType(tally.publishedByEventType)
        .build();
  }

  private <T> void replayEach(JpaRepository<T, String> repository,
      Function<T, List<DomainEvent>> events, Tally tally) {
    Pageable pageable = PageRequest.of(0, PAGE_SIZE, Sort.by("id"));
    Page<T> page;
    do {
      page = repository.findAll(pageable);
      List<DomainEvent> batch = page.getContent().stream()
          .map(events)
          .flatMap(List::stream)
          .toList();
      tally.count(batch, sender.sendAll(batch));
      pageable = page.nextPageable();
    } while (page.hasNext());
  }

  private static List<DomainEvent> jobPostEvents(JobPostEntity job, Tally tally) {
    boolean closed = job.getJobStatus() == JobStatus.CLOSE;
    if (closed && job.getClosedOn() == null) {
      tally.skippedJobPosts++;
      log.warn("message= Job post '{}' is closed without a close date and is not replayed",
          job.getId());
      return List.of();
    }

    List<DomainEvent> events = new ArrayList<>();
    events.add(DomainEvents.jobPostPublished(job, startOf(job.getCreatedOn())));
    if (closed) {
      events.add(DomainEvents.jobPostClosed(job, endOf(job.getClosedOn())));
    }
    return events;
  }

  private static List<DomainEvent> candidateEvents(UserEntity user) {
    List<DomainEvent> events = new ArrayList<>();
    if (!user.getSkills().isEmpty() || !user.getSimilarityTags().isEmpty()) {
      events.add(DomainEvents.candidateSkillsUpdated(user, startOf(skillsUpdatedOn(user))));
    }
    if (user.getExpectedSalary() != null) {
      events.add(DomainEvents.candidateExpectedSalaryUpdated(user, startOf(user.getCreatedOn())));
    }
    return events;
  }

  private static LocalDate skillsUpdatedOn(UserEntity user) {
    return user.getResumes().stream()
        .map(UserResumeEntity::getCreatedOn)
        .filter(Objects::nonNull)
        .max(Comparator.naturalOrder())
        .orElse(user.getCreatedOn());
  }

  static Instant startOf(LocalDate date) {
    return date.atStartOfDay(ZoneOffset.UTC).toInstant();
  }

  static Instant endOf(LocalDate date) {
    return date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().minusMillis(1);
  }

  private static final class Tally {

    private int published;
    private int failed;
    private int skippedJobPosts;
    private final Map<String, Integer> publishedByEventType = new LinkedHashMap<>();

    private void count(List<DomainEvent> sent, List<DomainEvent> unpublished) {
      failed += unpublished.size();
      sent.stream()
          .filter(event -> !unpublished.contains(event))
          .forEach(event -> {
            published++;
            publishedByEventType.merge(event.eventType(), 1, Integer::sum);
          });
    }
  }
}
