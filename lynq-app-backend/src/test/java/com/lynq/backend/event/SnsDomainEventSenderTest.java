package com.lynq.backend.event;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.lynq.backend.config.DomainEventsProperties;
import com.lynq.backend.event.payload.JobPostReopenedPayload;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Month;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.BatchResultErrorEntry;
import software.amazon.awssdk.services.sns.model.PublishBatchRequest;
import software.amazon.awssdk.services.sns.model.PublishBatchRequestEntry;
import software.amazon.awssdk.services.sns.model.PublishBatchResponse;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sns.model.PublishResponse;
import software.amazon.awssdk.services.sns.model.SnsException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SnsDomainEventSenderTest {

  private static final String TOPIC_ARN = "arn:aws:sns:us-east-1:000000000000:lynq-domain-events";
  private static final String JOB_ID = "77777777-7777-7777-7777-777777777777";
  private static final Instant OCCURRED_ON = Instant.parse("2026-09-20T10:00:00.123Z");

  @Mock
  private SnsClient snsClient;

  private SnsDomainEventSender sender;

  @BeforeEach
  void setUp() {
    ObjectMapper objectMapper = new ObjectMapper();
    objectMapper.registerModule(new JavaTimeModule());
    sender = new SnsDomainEventSender(snsClient, objectMapper,
        new DomainEventsProperties(TOPIC_ARN, 3, Duration.ZERO));
  }

  @Test
  void sendPublishesTheEnvelopeToTheTopicWithIsoDates() throws Exception {
    DomainEvent event = reopened(0);
    when(snsClient.publish(any(PublishRequest.class)))
        .thenReturn(PublishResponse.builder().build());

    boolean published = sender.send(event);

    assertThat(published, is(true));
    ArgumentCaptor<PublishRequest> captor = ArgumentCaptor.forClass(PublishRequest.class);
    verify(snsClient).publish(captor.capture());
    assertThat(captor.getValue().topicArn(), is(TOPIC_ARN));
    JsonNode message = new ObjectMapper().readTree(captor.getValue().message());
    assertThat(message.get("eventId").asText(), is(event.eventId().toString()));
    assertThat(message.get("eventType").asText(), is("JobPostReopened"));
    assertThat(message.get("aggregateType").asText(), is("JOB_POST"));
    assertThat(message.get("aggregateId").asText(), is(JOB_ID));
    assertThat(message.get("occurredOn").asText(), is("2026-09-20T10:00:00.123Z"));
    assertThat(message.get("payload").get("jobId").asText(), is(JOB_ID));
    assertThat(message.get("payload").get("reopenedOn").asText(), is("2026-09-20"));
  }

  @Test
  void sendRetriesUntilSnsAcceptsTheEvent() {
    when(snsClient.publish(any(PublishRequest.class)))
        .thenThrow(snsFailure())
        .thenThrow(snsFailure())
        .thenReturn(PublishResponse.builder().build());

    assertThat(sender.send(reopened(0)), is(true));

    verify(snsClient, times(3)).publish(any(PublishRequest.class));
  }

  @Test
  void sendGivesUpAfterTheConfiguredAttemptsWithoutThrowing() {
    when(snsClient.publish(any(PublishRequest.class))).thenThrow(snsFailure());

    assertThat(sender.send(reopened(0)), is(false));

    verify(snsClient, times(3)).publish(any(PublishRequest.class));
  }

  @Test
  void sendAllPublishesInBatchesOfTen() {
    List<DomainEvent> events = IntStream.range(0, 23).mapToObj(this::reopened).toList();
    when(snsClient.publishBatch(any(PublishBatchRequest.class)))
        .thenReturn(PublishBatchResponse.builder().build());

    List<DomainEvent> unpublished = sender.sendAll(events);

    assertThat(unpublished, is(empty()));
    ArgumentCaptor<PublishBatchRequest> captor = ArgumentCaptor.forClass(PublishBatchRequest.class);
    verify(snsClient, times(3)).publishBatch(captor.capture());
    assertThat(captor.getAllValues().stream()
        .map(request -> request.publishBatchRequestEntries().size())
        .toList(), contains(10, 10, 3));
    assertThat(captor.getAllValues().getFirst().topicArn(), is(TOPIC_ARN));
  }

  @Test
  void sendAllRetriesOnlyTheEntriesSnsRejected() {
    List<DomainEvent> events = List.of(reopened(0), reopened(1), reopened(2));
    when(snsClient.publishBatch(any(PublishBatchRequest.class)))
        .thenReturn(rejecting("1"))
        .thenReturn(PublishBatchResponse.builder().build());

    List<DomainEvent> unpublished = sender.sendAll(events);

    assertThat(unpublished, is(empty()));
    ArgumentCaptor<PublishBatchRequest> captor = ArgumentCaptor.forClass(PublishBatchRequest.class);
    verify(snsClient, times(2)).publishBatch(captor.capture());
    assertThat(captor.getAllValues().get(1).publishBatchRequestEntries().stream()
        .map(PublishBatchRequestEntry::id)
        .toList(), contains("1"));
  }

  @Test
  void sendAllReturnsTheEventsSnsKeepsRejecting() {
    List<DomainEvent> events = List.of(reopened(0), reopened(1));
    when(snsClient.publishBatch(any(PublishBatchRequest.class))).thenReturn(rejecting("0"));

    List<DomainEvent> unpublished = sender.sendAll(events);

    assertThat(unpublished, contains(events.getFirst()));
    verify(snsClient, times(3)).publishBatch(any(PublishBatchRequest.class));
  }

  @Test
  void sendAllReturnsTheWholeBatchWhenSnsCannotBeReached() {
    List<DomainEvent> events = List.of(reopened(0), reopened(1));
    when(snsClient.publishBatch(any(PublishBatchRequest.class))).thenThrow(snsFailure());

    List<DomainEvent> unpublished = sender.sendAll(events);

    assertThat(unpublished, contains(events.get(0), events.get(1)));
    verify(snsClient, times(3)).publishBatch(any(PublishBatchRequest.class));
  }

  private DomainEvent reopened(int day) {
    LocalDate reopenedOn = LocalDate.of(2026, Month.SEPTEMBER, 20).plusDays(day);
    return new DomainEvent(UUID.nameUUIDFromBytes(("event-" + day).getBytes()),
        "JobPostReopened", "JOB_POST", JOB_ID, OCCURRED_ON,
        new JobPostReopenedPayload(JOB_ID, reopenedOn));
  }

  private static PublishBatchResponse rejecting(String id) {
    return PublishBatchResponse.builder()
        .failed(BatchResultErrorEntry.builder()
            .id(id)
            .code("InternalError")
            .message("try again")
            .senderFault(false)
            .build())
        .build();
  }

  private static SnsException snsFailure() {
    return (SnsException) SnsException.builder().message("SNS is unavailable").build();
  }
}
