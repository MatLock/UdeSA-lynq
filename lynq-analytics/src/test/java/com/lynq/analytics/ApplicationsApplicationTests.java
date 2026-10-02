package com.lynq.analytics;

import com.lynq.analytics.model.ApplicationEntity;
import com.lynq.analytics.repository.ApplicationRepository;
import com.lynq.analytics.repository.DomainEventRepository;
import com.lynq.analytics.repository.JobPostRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.PurgeQueueRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class ApplicationsApplicationTests extends AbstractE2ETest {

  private static final Duration TIMEOUT = Duration.ofSeconds(30);

  private static final String JOB_ID = "77777777-7777-7777-7777-777777777777";
  private static final String APPLICATION_ID = "33333333-3333-3333-3333-333333333333";
  private static final String USER_ID = "11111111-1111-1111-1111-111111111111";

  private static final String PUBLISHED_EVENT_ID = "c1b2c3d4-0001-5000-8000-000000000001";
  private static final String SUBMITTED_EVENT_ID = "c1b2c3d4-0002-5000-8000-000000000002";
  private static final String INVALID_SCORE_EVENT_ID = "c1b2c3d4-0003-5000-8000-000000000003";

  private static final String PUBLISHED = """
      {"jobId": "%s", "title": "Backend Developer", "workType": "REMOTE", "source": "LYNQ",
       "skills": ["Java"], "similarityTags": ["backend"], "publishedOn": "2026-09-20"}"""
      .formatted(JOB_ID);

  private static final String SUBMITTED = """
      {"applicationId": "%s", "jobId": "%s", "userId": "%s", "appliedOn": "2026-09-22",
       "lynqScore": 72}""".formatted(APPLICATION_ID, JOB_ID, USER_ID);

  private static final String INVALID_SCORE = """
      {"applicationId": "%s", "jobId": "%s", "userId": "%s", "appliedOn": "2026-09-22",
       "lynqScore": 250}""".formatted(APPLICATION_ID, JOB_ID, USER_ID);

  @Autowired
  private DomainEventRepository domainEventRepository;

  @Autowired
  private JobPostRepository jobPostRepository;

  @Autowired
  private ApplicationRepository applicationRepository;

  @BeforeEach
  void setUp() {
    sqsTestClient.purgeQueue(PurgeQueueRequest.builder().queueUrl(eventsQueueUrl).build());
    sqsTestClient.purgeQueue(PurgeQueueRequest.builder().queueUrl(eventsDlqUrl).build());
    applicationRepository.deleteAll();
    jobPostRepository.deleteAll();
    domainEventRepository.deleteAll();
  }

  @Test
  void projectsAnApplicationWithTheScoreOfTheEvent() {
    publishAndAwait(jobPostEnvelope(PUBLISHED_EVENT_ID, PUBLISHED), PUBLISHED_EVENT_ID);

    publishAndAwait(applicationEnvelope(SUBMITTED_EVENT_ID, SUBMITTED), SUBMITTED_EVENT_ID);

    ApplicationEntity application = applicationRepository.findById(APPLICATION_ID).orElseThrow();
    assertThat(application.getJobId(), is(JOB_ID));
    assertThat(application.getCandidateId(), is(USER_ID));
    assertThat(application.getAppliedOn(), is(LocalDate.parse("2026-09-22")));
    assertThat(application.getLynqScore(), is(72));
  }

  @Test
  void projectsAnApplicationThatArrivesBeforeItsJobPost() {
    publish(applicationEnvelope(SUBMITTED_EVENT_ID, SUBMITTED));
    publish(jobPostEnvelope(PUBLISHED_EVENT_ID, PUBLISHED));

    await().atMost(TIMEOUT).until(() -> domainEventRepository.existsByEventId(PUBLISHED_EVENT_ID)
        && domainEventRepository.existsByEventId(SUBMITTED_EVENT_ID));

    assertThat(applicationRepository.findById(APPLICATION_ID).orElseThrow().getLynqScore(),
        is(72));
  }

  @Test
  void sendsAnApplicationForAJobPostThatIsNeverPublishedToTheDeadLetterQueue() {
    String submitted = applicationEnvelope(SUBMITTED_EVENT_ID, SUBMITTED);
    publish(submitted);

    List<Message> deadLetters = awaitDeadLetters();

    assertThat(deadLetters.getFirst().body(), is(submitted));
    assertThat(domainEventRepository.existsByEventId(SUBMITTED_EVENT_ID), is(false));
    assertThat(applicationRepository.existsById(APPLICATION_ID), is(false));
  }

  @Test
  void sendsAnApplicationWithAScoreOutOfRangeToTheDeadLetterQueue() {
    publishAndAwait(jobPostEnvelope(PUBLISHED_EVENT_ID, PUBLISHED), PUBLISHED_EVENT_ID);
    String invalid = applicationEnvelope(INVALID_SCORE_EVENT_ID, INVALID_SCORE);
    publish(invalid);

    List<Message> deadLetters = awaitDeadLetters();

    assertThat(deadLetters.getFirst().body(), is(invalid));
    assertThat(domainEventRepository.existsByEventId(INVALID_SCORE_EVENT_ID), is(false));
    assertThat(applicationRepository.existsById(APPLICATION_ID), is(false));
  }

  private List<Message> awaitDeadLetters() {
    return await().atMost(TIMEOUT)
        .until(() -> receive(eventsDlqUrl), messages -> !messages.isEmpty());
  }

  private void publishAndAwait(String message, String eventId) {
    publish(message);
    await().atMost(TIMEOUT).until(() -> domainEventRepository.existsByEventId(eventId));
  }

  private String jobPostEnvelope(String eventId, String payload) {
    return """
        {"eventId": "%s",
         "eventType": "JobPostPublished",
         "aggregateType": "JOB_POST",
         "aggregateId": "%s",
         "occurredOn": "2026-09-20T10:00:00Z",
         "payload": %s}""".formatted(eventId, JOB_ID, payload);
  }

  private String applicationEnvelope(String eventId, String payload) {
    return """
        {"eventId": "%s",
         "eventType": "ApplicationSubmitted",
         "aggregateType": "APPLICATION",
         "aggregateId": "%s",
         "occurredOn": "2026-09-22T10:00:00Z",
         "payload": %s}""".formatted(eventId, APPLICATION_ID, payload);
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
