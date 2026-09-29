package com.lynq.analytics;

import com.lynq.analytics.enums.JobStatus;
import com.lynq.analytics.model.JobPostEntity;
import com.lynq.analytics.repository.DomainEventRepository;
import com.lynq.analytics.repository.JobPostRepository;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.PurgeQueueRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

class JobPostsApplicationTests extends AbstractE2ETest {

  private static final Duration TIMEOUT = Duration.ofSeconds(30);

  private static final String JOB_ID = "77777777-7777-7777-7777-777777777777";

  private static final String PUBLISHED_EVENT_ID = "a1b2c3d4-0001-5000-8000-000000000001";
  private static final String UPDATED_EVENT_ID = "a1b2c3d4-0002-5000-8000-000000000002";
  private static final String STALE_UPDATED_EVENT_ID = "a1b2c3d4-0003-5000-8000-000000000003";
  private static final String CLOSED_EVENT_ID = "a1b2c3d4-0004-5000-8000-000000000004";
  private static final String REOPENED_EVENT_ID = "a1b2c3d4-0005-5000-8000-000000000005";

  private static final String PUBLISHED = """
      {"jobId": "%s", "title": "Backend Developer", "category": "tecnologia",
       "workType": "REMOTE", "source": "LYNQ",
       "companyId": "22222222-2222-2222-2222-222222222222",
       "createdByUserId": "11111111-1111-1111-1111-111111111111",
       "salaryRangeDown": 1500000, "salaryRangeTop": 2200000, "salaryCurrency": "ARS",
       "skills": ["Java", "Spring"], "similarityTags": ["backend", "jvm"],
       "publishedOn": "2026-09-20", "synthetic": false}""".formatted(JOB_ID);

  private static final String UPDATED = """
      {"jobId": "%s", "title": "Senior Backend Developer", "workType": "REMOTE",
       "salaryRangeDown": 1800000, "salaryRangeTop": 2600000, "salaryCurrency": "ARS",
       "skills": ["Java", "Kotlin"], "similarityTags": ["backend"]}""".formatted(JOB_ID);

  private static final String STALE_UPDATED = """
      {"jobId": "%s", "title": "Stale title", "workType": "IN_OFFICE",
       "skills": ["Cobol"], "similarityTags": ["mainframe"]}""".formatted(JOB_ID);

  private static final String CLOSED = """
      {"jobId": "%s", "closedOn": "2026-09-24", "closeReason": "OWNER"}""".formatted(JOB_ID);

  private static final String REOPENED = """
      {"jobId": "%s", "reopenedOn": "2026-09-26"}""".formatted(JOB_ID);

  @Autowired
  private DomainEventRepository domainEventRepository;

  @Autowired
  private JobPostRepository jobPostRepository;

  @Autowired
  private TransactionTemplate transactionTemplate;

  @BeforeEach
  void setUp() {
    sqsTestClient.purgeQueue(PurgeQueueRequest.builder().queueUrl(eventsQueueUrl).build());
    sqsTestClient.purgeQueue(PurgeQueueRequest.builder().queueUrl(eventsDlqUrl).build());
    jobPostRepository.deleteAll();
    domainEventRepository.deleteAll();
  }

  @Test
  void projectsAJobPostThroughItsLifecycle() {
    publishAndAwait(PUBLISHED_EVENT_ID, "JobPostPublished", "2026-09-20T10:00:00Z", PUBLISHED);
    JobPostEntity published = jobPost().orElseThrow();
    assertThat(published.getTitle(), is("Backend Developer"));
    assertThat(published.getCategory(), is("tecnologia"));
    assertThat(published.getStatus(), is(JobStatus.OPEN));
    assertThat(published.getPublishedOn(), is(LocalDate.parse("2026-09-20")));
    assertThat(published.getSkills(), containsInAnyOrder("Java", "Spring"));
    assertThat(published.getTags(), containsInAnyOrder("backend", "jvm"));

    publishAndAwait(UPDATED_EVENT_ID, "JobPostUpdated", "2026-09-22T10:00:00Z", UPDATED);
    JobPostEntity updated = jobPost().orElseThrow();
    assertThat(updated.getTitle(), is("Senior Backend Developer"));
    assertThat(updated.getSalaryRangeTop(), is(2600000));
    assertThat(updated.getSkills(), containsInAnyOrder("Java", "Kotlin"));
    assertThat(updated.getTags(), containsInAnyOrder("backend"));

    publishAndAwait(CLOSED_EVENT_ID, "JobPostClosed", "2026-09-24T10:00:00Z", CLOSED);
    JobPostEntity closed = jobPost().orElseThrow();
    assertThat(closed.getStatus(), is(JobStatus.CLOSE));
    assertThat(closed.getClosedOn(), is(LocalDate.parse("2026-09-24")));
    assertThat(closed.getCloseReason(), is("OWNER"));

    publishAndAwait(REOPENED_EVENT_ID, "JobPostReopened", "2026-09-26T10:00:00Z", REOPENED);
    JobPostEntity reopened = jobPost().orElseThrow();
    assertThat(reopened.getStatus(), is(JobStatus.OPEN));
    assertThat(reopened.getReopenedOn(), is(LocalDate.parse("2026-09-26")));
    assertThat(reopened.getClosedOn(), is(nullValue()));
    assertThat(reopened.getPublishedOn(), is(LocalDate.parse("2026-09-20")));
  }

  @Test
  void discardsAnUpdateOlderThanTheStoredOneButStillRecordsIt() {
    publishAndAwait(PUBLISHED_EVENT_ID, "JobPostPublished", "2026-09-20T10:00:00Z", PUBLISHED);
    publishAndAwait(UPDATED_EVENT_ID, "JobPostUpdated", "2026-09-22T10:00:00Z", UPDATED);

    publishAndAwait(STALE_UPDATED_EVENT_ID, "JobPostUpdated", "2026-09-21T10:00:00Z",
        STALE_UPDATED);

    JobPostEntity job = jobPost().orElseThrow();
    assertThat(job.getTitle(), is("Senior Backend Developer"));
    assertThat(job.getSkills(), containsInAnyOrder("Java", "Kotlin"));
    assertThat(domainEventRepository.existsByEventId(STALE_UPDATED_EVENT_ID), is(true));
  }

  @Test
  void keepsAJobPostClosedWhenTheCloseArrivesAfterANewerUpdate() {
    publishAndAwait(PUBLISHED_EVENT_ID, "JobPostPublished", "2026-09-20T10:00:00Z", PUBLISHED);
    publishAndAwait(UPDATED_EVENT_ID, "JobPostUpdated", "2026-09-25T10:00:00Z", UPDATED);

    publishAndAwait(CLOSED_EVENT_ID, "JobPostClosed", "2026-09-24T10:00:00Z", CLOSED);

    JobPostEntity job = jobPost().orElseThrow();
    assertThat(job.getStatus(), is(JobStatus.CLOSE));
    assertThat(job.getTitle(), is("Senior Backend Developer"));
  }

  @Test
  void projectsAnUpdateThatArrivesBeforeItsPublish() {
    publish(envelope(UPDATED_EVENT_ID, "JobPostUpdated", "2026-09-22T10:00:00Z", UPDATED));
    publish(envelope(PUBLISHED_EVENT_ID, "JobPostPublished", "2026-09-20T10:00:00Z", PUBLISHED));

    await().atMost(TIMEOUT).until(() -> domainEventRepository.existsByEventId(PUBLISHED_EVENT_ID)
        && domainEventRepository.existsByEventId(UPDATED_EVENT_ID));

    JobPostEntity job = jobPost().orElseThrow();
    assertThat(job.getTitle(), is("Senior Backend Developer"));
    assertThat(job.getSource(), is("LYNQ"));
    assertThat(job.getSkills(), containsInAnyOrder("Java", "Kotlin"));
  }

  @Test
  void sendsAnUpdateForAJobPostThatIsNeverPublishedToTheDeadLetterQueue() {
    String update = envelope(UPDATED_EVENT_ID, "JobPostUpdated", "2026-09-22T10:00:00Z", UPDATED);
    publish(update);

    List<Message> deadLetters = await().atMost(TIMEOUT)
        .until(() -> receive(eventsDlqUrl), messages -> !messages.isEmpty());

    assertThat(deadLetters.getFirst().body(), is(update));
    assertThat(domainEventRepository.existsByEventId(UPDATED_EVENT_ID), is(false));
    assertThat(jobPostRepository.existsById(JOB_ID), is(false));
  }

  private void publishAndAwait(String eventId, String eventType, String occurredOn,
      String payload) {
    publish(envelope(eventId, eventType, occurredOn, payload));
    await().atMost(TIMEOUT).until(() -> domainEventRepository.existsByEventId(eventId));
  }

  private Optional<JobPostEntity> jobPost() {
    return transactionTemplate.execute(status -> {
      Optional<JobPostEntity> job = jobPostRepository.findById(JOB_ID);
      job.ifPresent(found -> {
        Hibernate.initialize(found.getSkills());
        Hibernate.initialize(found.getTags());
      });
      return job;
    });
  }

  private String envelope(String eventId, String eventType, String occurredOn, String payload) {
    return """
        {"eventId": "%s",
         "eventType": "%s",
         "aggregateType": "JOB_POST",
         "aggregateId": "%s",
         "occurredOn": "%s",
         "payload": %s}""".formatted(eventId, eventType, JOB_ID, occurredOn, payload);
  }

  private void publish(String message) {
    snsTestClient.publish(PublishRequest.builder()
        .topicArn(domainEventsTopicArn)
        .message(message)
        .build());
  }

  private List<Message> receive(String queueUrl) {
    return sqsTestClient.receiveMessage(ReceiveMessageRequest.builder()
        .queueUrl(queueUrl)
        .waitTimeSeconds(1)
        .build()).messages();
  }
}
