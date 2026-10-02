package com.lynq.analytics;

import com.lynq.analytics.model.CandidateEntity;
import com.lynq.analytics.repository.CandidateRepository;
import com.lynq.analytics.repository.DomainEventRepository;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sqs.model.PurgeQueueRequest;

import java.time.Duration;
import java.util.Optional;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

class CandidatesApplicationTests extends AbstractE2ETest {

  private static final Duration TIMEOUT = Duration.ofSeconds(30);

  private static final String USER_ID = "11111111-1111-1111-1111-111111111111";

  private static final String SKILLS_EVENT_ID = "b1b2c3d4-0001-5000-8000-000000000001";
  private static final String NEWER_SKILLS_EVENT_ID = "b1b2c3d4-0002-5000-8000-000000000002";
  private static final String STALE_SKILLS_EVENT_ID = "b1b2c3d4-0003-5000-8000-000000000003";
  private static final String SALARY_EVENT_ID = "b1b2c3d4-0004-5000-8000-000000000004";
  private static final String SALARY_CLEARED_EVENT_ID = "b1b2c3d4-0005-5000-8000-000000000005";

  private static final String SKILLS = """
      {"userId": "%s", "skills": ["Java", "Spring"], "similarityTags": ["backend", "jvm"]}""".formatted(USER_ID);

  private static final String NEWER_SKILLS = """
      {"userId": "%s", "skills": ["Java", "Kotlin"], "similarityTags": ["backend"]}"""
      .formatted(USER_ID);

  private static final String STALE_SKILLS = """
      {"userId": "%s", "skills": ["Cobol"], "similarityTags": ["mainframe"]}""".formatted(USER_ID);

  private static final String SALARY = """
      {"userId": "%s", "expectedSalary": 2000000, "currency": "ARS"}""".formatted(USER_ID);

  private static final String SALARY_CLEARED = """
      {"userId": "%s", "expectedSalary": null, "currency": null}""".formatted(USER_ID);

  @Autowired
  private DomainEventRepository domainEventRepository;

  @Autowired
  private CandidateRepository candidateRepository;

  @Autowired
  private TransactionTemplate transactionTemplate;

  @BeforeEach
  void setUp() {
    sqsTestClient.purgeQueue(PurgeQueueRequest.builder().queueUrl(eventsQueueUrl).build());
    sqsTestClient.purgeQueue(PurgeQueueRequest.builder().queueUrl(eventsDlqUrl).build());
    candidateRepository.deleteAll();
    domainEventRepository.deleteAll();
  }

  @Test
  void projectsACandidateThroughItsSkillsAndExpectedSalary() {
    publishAndAwait(SKILLS_EVENT_ID, "CandidateSkillsUpdated", "2026-09-20T10:00:00Z", SKILLS);
    CandidateEntity withSkills = candidate().orElseThrow();
    assertThat(withSkills.getSkills(), containsInAnyOrder("Java", "Spring"));
    assertThat(withSkills.getTags(), containsInAnyOrder("backend", "jvm"));
    assertThat(withSkills.getExpectedSalary(), is(nullValue()));

    publishAndAwait(SALARY_EVENT_ID, "CandidateExpectedSalaryUpdated", "2026-09-21T10:00:00Z",
        SALARY);
    CandidateEntity withSalary = candidate().orElseThrow();
    assertThat(withSalary.getExpectedSalary(), is(2000000));
    assertThat(withSalary.getExpectedSalaryCurrency(), is("ARS"));
    assertThat(withSalary.getSkills(), containsInAnyOrder("Java", "Spring"));

    publishAndAwait(NEWER_SKILLS_EVENT_ID, "CandidateSkillsUpdated", "2026-09-22T10:00:00Z",
        NEWER_SKILLS);
    CandidateEntity reskilled = candidate().orElseThrow();
    assertThat(reskilled.getSkills(), containsInAnyOrder("Java", "Kotlin"));
    assertThat(reskilled.getTags(), containsInAnyOrder("backend"));
    assertThat(reskilled.getExpectedSalary(), is(2000000));

    publishAndAwait(SALARY_CLEARED_EVENT_ID, "CandidateExpectedSalaryUpdated",
        "2026-09-23T10:00:00Z", SALARY_CLEARED);
    CandidateEntity cleared = candidate().orElseThrow();
    assertThat(cleared.getExpectedSalary(), is(nullValue()));
    assertThat(cleared.getExpectedSalaryCurrency(), is(nullValue()));
  }

  @Test
  void discardsSkillsOlderThanTheStoredOnesButStillRecordsThem() {
    publishAndAwait(NEWER_SKILLS_EVENT_ID, "CandidateSkillsUpdated", "2026-09-22T10:00:00Z",
        NEWER_SKILLS);

    publishAndAwait(STALE_SKILLS_EVENT_ID, "CandidateSkillsUpdated", "2026-09-21T10:00:00Z",
        STALE_SKILLS);

    CandidateEntity candidate = candidate().orElseThrow();
    assertThat(candidate.getSkills(), containsInAnyOrder("Java", "Kotlin"));
    assertThat(domainEventRepository.existsByEventId(STALE_SKILLS_EVENT_ID), is(true));
  }

  @Test
  void keepsAnExpectedSalaryThatArrivesAfterNewerSkills() {
    publishAndAwait(NEWER_SKILLS_EVENT_ID, "CandidateSkillsUpdated", "2026-09-22T10:00:00Z",
        NEWER_SKILLS);

    publishAndAwait(SALARY_EVENT_ID, "CandidateExpectedSalaryUpdated", "2026-09-21T10:00:00Z",
        SALARY);

    CandidateEntity candidate = candidate().orElseThrow();
    assertThat(candidate.getExpectedSalary(), is(2000000));
    assertThat(candidate.getSkills(), containsInAnyOrder("Java", "Kotlin"));
  }

  private void publishAndAwait(String eventId, String eventType, String occurredOn,
      String payload) {
    snsTestClient.publish(PublishRequest.builder()
        .topicArn(domainEventsTopicArn)
        .message(envelope(eventId, eventType, occurredOn, payload))
        .build());
    await().atMost(TIMEOUT).until(() -> domainEventRepository.existsByEventId(eventId));
  }

  private Optional<CandidateEntity> candidate() {
    return transactionTemplate.execute(status -> {
      Optional<CandidateEntity> candidate = candidateRepository.findById(USER_ID);
      candidate.ifPresent(found -> {
        Hibernate.initialize(found.getSkills());
        Hibernate.initialize(found.getTags());
      });
      return candidate;
    });
  }

  private String envelope(String eventId, String eventType, String occurredOn, String payload) {
    return """
        {"eventId": "%s",
         "eventType": "%s",
         "aggregateType": "CANDIDATE",
         "aggregateId": "%s",
         "occurredOn": "%s",
         "payload": %s}""".formatted(eventId, eventType, USER_ID, occurredOn, payload);
  }
}
