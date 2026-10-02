package com.lynq.analytics;

import com.lynq.analytics.model.DomainEventEntity;
import com.lynq.analytics.repository.DomainEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.PurgeQueueRequest;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

class DomainEventsApplicationTests extends AbstractE2ETest {

  private static final Duration TIMEOUT = Duration.ofSeconds(30);

  private static final String EVENT_ID = "5c8f3a3e-0b7e-5d61-9c1a-2f4b8e6d7a10";
  private static final String DUPLICATED_EVENT_ID = "0e4c1d52-6a9b-5f3e-8d27-b1c6a4f9e823";
  private static final String EVENT_TYPE = "JobPostViewed";
  private static final String AGGREGATE_TYPE = "JOB_POST";
  private static final String AGGREGATE_ID = "77777777-7777-7777-7777-777777777777";
  private static final String OCCURRED_ON = "2026-09-29T14:03:27.125Z";
  private static final String PAYLOAD = """
      {"jobId": "77777777-7777-7777-7777-777777777777",
       "userId": "11111111-1111-1111-1111-111111111111",
       "viewedOn": "2026-09-29"}""";

  private static final String NOT_JSON = "this is not a domain event";
  private static final String WITHOUT_EVENT_TYPE = """
      {"eventId": "9a7d2e11-3c4b-5e6f-8a90-b1c2d3e4f506",
       "aggregateType": "JOB_POST",
       "aggregateId": "77777777-7777-7777-7777-777777777777",
       "occurredOn": "2026-09-29T14:03:27Z",
       "payload": {"jobId": "77777777-7777-7777-7777-777777777777"}}""";

  @Autowired
  private DomainEventRepository domainEventRepository;

  @Autowired
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    sqsTestClient.purgeQueue(PurgeQueueRequest.builder().queueUrl(eventsQueueUrl).build());
    sqsTestClient.purgeQueue(PurgeQueueRequest.builder().queueUrl(eventsDlqUrl).build());
    domainEventRepository.deleteAll();
  }

  @Test
  void recordsAnEventPublishedToTheDomainEventsTopic() {
    publish(envelope(EVENT_ID));

    DomainEventEntity recorded = await().atMost(TIMEOUT)
        .until(() -> domainEventRepository.findByEventId(EVENT_ID), Optional::isPresent)
        .orElseThrow();

    assertThat(recorded.getEventType(), is(EVENT_TYPE));
    assertThat(recorded.getAggregateType(), is(AGGREGATE_TYPE));
    assertThat(recorded.getAggregateId(), is(AGGREGATE_ID));
    assertThat(recorded.getOccurredOn(), is(Instant.parse(OCCURRED_ON)));
    assertThat(recorded.getReceivedOn(), is(notNullValue()));
    assertThat(parse(recorded.getPayload()), is(parse(PAYLOAD)));
  }

  @Test
  void acknowledgesADuplicatedEventAndKeepsASingleRow() {
    publish(envelope(DUPLICATED_EVENT_ID));
    publish(envelope(DUPLICATED_EVENT_ID));

    await().atMost(TIMEOUT).until(() ->
        domainEventRepository.existsByEventId(DUPLICATED_EVENT_ID) && isDrained(eventsQueueUrl));

    assertThat(domainEventRepository.findAll().stream().map(DomainEventEntity::getEventId).toList(),
        contains(DUPLICATED_EVENT_ID));
    assertThat(receive(eventsDlqUrl), hasSize(0));
  }

  @Test
  void sendsAMessageThatIsNotJsonToTheDeadLetterQueue() {
    publish(NOT_JSON);

    List<Message> deadLetters = awaitDeadLetters();

    assertThat(deadLetters.getFirst().body(), is(NOT_JSON));
    assertThat(domainEventRepository.count(), is(0L));
  }

  @Test
  void sendsAnEventWithoutEventTypeToTheDeadLetterQueue() {
    publish(WITHOUT_EVENT_TYPE);

    List<Message> deadLetters = awaitDeadLetters();

    assertThat(parse(deadLetters.getFirst().body()), is(parse(WITHOUT_EVENT_TYPE)));
    assertThat(domainEventRepository.count(), is(0L));
  }

  private List<Message> awaitDeadLetters() {
    return await().atMost(TIMEOUT).until(() -> receive(eventsDlqUrl), messages -> !messages.isEmpty());
  }

  private String envelope(String eventId) {
    return """
        {"eventId": "%s",
         "eventType": "%s",
         "aggregateType": "%s",
         "aggregateId": "%s",
         "occurredOn": "%s",
         "payload": %s}""".formatted(eventId, EVENT_TYPE, AGGREGATE_TYPE, AGGREGATE_ID,
        OCCURRED_ON, PAYLOAD);
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

  private boolean isDrained(String queueUrl) {
    Map<QueueAttributeName, String> attributes = sqsTestClient.getQueueAttributes(
        GetQueueAttributesRequest.builder()
            .queueUrl(queueUrl)
            .attributeNames(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES,
                QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_NOT_VISIBLE)
            .build()).attributes();
    return "0".equals(attributes.get(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES))
        && "0".equals(attributes.get(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_NOT_VISIBLE));
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> parse(String json) {
    return objectMapper.readValue(json, Map.class);
  }
}
