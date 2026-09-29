package com.lynq.analytics.service;

import com.lynq.analytics.exceptions.InvalidDomainEventException;
import com.lynq.analytics.listener.message.DomainEventMessage;
import com.lynq.analytics.model.DomainEventEntity;
import com.lynq.analytics.repository.DomainEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.JsonNodeFactory;

import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DomainEventServiceTest {

  private static final UUID EVENT_ID = UUID.fromString("5c8f3a3e-0b7e-5d61-9c1a-2f4b8e6d7a10");
  private static final String EVENT_TYPE = "JobPostClosed";
  private static final String AGGREGATE_TYPE = "JOB_POST";
  private static final String AGGREGATE_ID = "77777777-7777-7777-7777-777777777777";
  private static final Instant OCCURRED_ON = Instant.parse("2026-09-29T14:03:27Z");
  private static final String PAYLOAD_JSON = """
      {"jobId":"77777777-7777-7777-7777-777777777777","closedOn":"2026-09-29","closeReason":"OWNER"}""";

  private static final JsonNode PAYLOAD = JsonMapper.builder().build().readTree(PAYLOAD_JSON);

  @Mock
  private DomainEventRepository domainEventRepository;

  private DomainEventService domainEventService;

  @BeforeEach
  void setUp() {
    domainEventService = new DomainEventService(domainEventRepository);
  }

  @Test
  void recordsANewEventWithItsEnvelopeAndRawPayload() {
    when(domainEventRepository.existsByEventId(EVENT_ID.toString())).thenReturn(false);
    ArgumentCaptor<DomainEventEntity> captor = ArgumentCaptor.forClass(DomainEventEntity.class);

    boolean recorded = domainEventService.record(message(EVENT_TYPE, AGGREGATE_TYPE, AGGREGATE_ID,
        OCCURRED_ON, PAYLOAD));

    assertThat(recorded, is(true));
    verify(domainEventRepository).saveAndFlush(captor.capture());
    DomainEventEntity entity = captor.getValue();
    assertThat(entity.getEventId(), is(EVENT_ID.toString()));
    assertThat(entity.getEventType(), is(EVENT_TYPE));
    assertThat(entity.getAggregateType(), is(AGGREGATE_TYPE));
    assertThat(entity.getAggregateId(), is(AGGREGATE_ID));
    assertThat(entity.getOccurredOn(), is(OCCURRED_ON));
    assertThat(entity.getReceivedOn(), is(notNullValue()));
    assertThat(JsonMapper.builder().build().readTree(entity.getPayload()), is(PAYLOAD));
  }

  @Test
  void skipsAnEventThatIsAlreadyRecorded() {
    when(domainEventRepository.existsByEventId(EVENT_ID.toString())).thenReturn(true);

    boolean recorded = domainEventService.record(message(EVENT_TYPE, AGGREGATE_TYPE, AGGREGATE_ID,
        OCCURRED_ON, PAYLOAD));

    assertThat(recorded, is(false));
    verify(domainEventRepository, never()).saveAndFlush(any());
  }

  @Test
  void rejectsAnEmptyMessage() {
    assertRejected(null, "Domain event is empty");
  }

  @Test
  void rejectsAnEventWithoutEventId() {
    assertRejected(new DomainEventMessage(null, EVENT_TYPE, AGGREGATE_TYPE, AGGREGATE_ID,
        OCCURRED_ON, PAYLOAD), "Domain event has no eventId");
  }

  @Test
  void rejectsAnEventWithABlankEventType() {
    assertRejected(message(" ", AGGREGATE_TYPE, AGGREGATE_ID, OCCURRED_ON, PAYLOAD),
        "Domain event '" + EVENT_ID + "' has no eventType");
  }

  @Test
  void rejectsAnEventWithAnEventTypeLongerThanItsColumn() {
    assertRejected(message("E".repeat(65), AGGREGATE_TYPE, AGGREGATE_ID, OCCURRED_ON, PAYLOAD),
        "Domain event '" + EVENT_ID + "' eventType is longer than 64 characters");
  }

  @Test
  void rejectsAnEventWithoutAggregateType() {
    assertRejected(message(EVENT_TYPE, null, AGGREGATE_ID, OCCURRED_ON, PAYLOAD),
        "Domain event '" + EVENT_ID + "' has no aggregateType");
  }

  @Test
  void rejectsAnEventWithAnAggregateIdLongerThanItsColumn() {
    assertRejected(message(EVENT_TYPE, AGGREGATE_TYPE, AGGREGATE_ID + "0", OCCURRED_ON, PAYLOAD),
        "Domain event '" + EVENT_ID + "' aggregateId is longer than 36 characters");
  }

  @Test
  void rejectsAnEventWithoutOccurredOn() {
    assertRejected(message(EVENT_TYPE, AGGREGATE_TYPE, AGGREGATE_ID, null, PAYLOAD),
        "Domain event '" + EVENT_ID + "' has no occurredOn");
  }

  @Test
  void rejectsAnEventWithoutPayload() {
    assertRejected(message(EVENT_TYPE, AGGREGATE_TYPE, AGGREGATE_ID, OCCURRED_ON, null),
        "Domain event '" + EVENT_ID + "' has no payload object");
  }

  @Test
  void rejectsAnEventWhosePayloadIsNotAnObject() {
    assertRejected(message(EVENT_TYPE, AGGREGATE_TYPE, AGGREGATE_ID, OCCURRED_ON,
            JsonNodeFactory.instance.textNode(PAYLOAD_JSON)),
        "Domain event '" + EVENT_ID + "' has no payload object");
  }

  @Test
  void isRecordedLooksTheEventIdUp() {
    when(domainEventRepository.existsByEventId(EVENT_ID.toString())).thenReturn(true);

    assertThat(domainEventService.isRecorded(EVENT_ID), is(true));
  }

  @Test
  void isRecordedIsFalseWithoutEventId() {
    assertThat(domainEventService.isRecorded(null), is(false));
    verifyNoInteractions(domainEventRepository);
  }

  private void assertRejected(DomainEventMessage message, String reason) {
    InvalidDomainEventException exception = assertThrows(InvalidDomainEventException.class,
        () -> domainEventService.record(message));

    assertThat(exception.getMessage(), is(reason));
    verifyNoInteractions(domainEventRepository);
  }

  private DomainEventMessage message(String eventType, String aggregateType, String aggregateId,
      Instant occurredOn, JsonNode payload) {
    return new DomainEventMessage(EVENT_ID, eventType, aggregateType, aggregateId, occurredOn,
        payload);
  }
}
