package com.lynq.analytics.listener;

import com.lynq.analytics.listener.message.DomainEventMessage;
import com.lynq.analytics.service.DomainEventService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import tools.jackson.databind.node.JsonNodeFactory;

import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DomainEventListenerTest {

  private static final UUID EVENT_ID = UUID.fromString("5c8f3a3e-0b7e-5d61-9c1a-2f4b8e6d7a10");
  private static final DomainEventMessage MESSAGE = new DomainEventMessage(EVENT_ID,
      "JobPostReopened", "JOB_POST", "77777777-7777-7777-7777-777777777777",
      Instant.parse("2026-09-29T14:03:27Z"), JsonNodeFactory.instance.objectNode());

  @Mock
  private DomainEventService domainEventService;

  private DomainEventListener domainEventListener;

  @BeforeEach
  void setUp() {
    domainEventListener = new DomainEventListener(domainEventService);
  }

  @Test
  void recordsTheEvent() {
    domainEventListener.onDomainEvent(MESSAGE);

    verify(domainEventService).record(MESSAGE);
    verify(domainEventService, never()).isRecorded(EVENT_ID);
  }

  @Test
  void acknowledgesAnEventThatAnotherConsumerRecordedFirst() {
    when(domainEventService.record(MESSAGE))
        .thenThrow(new DataIntegrityViolationException("Duplicate entry for uk_domain_events_event_id"));
    when(domainEventService.isRecorded(EVENT_ID)).thenReturn(true);

    domainEventListener.onDomainEvent(MESSAGE);

    verify(domainEventService).isRecorded(EVENT_ID);
  }

  @Test
  void rethrowsAnIntegrityViolationThatIsNotADuplicate() {
    DataIntegrityViolationException violation =
        new DataIntegrityViolationException("Data too long for column 'aggregate_id'");
    when(domainEventService.record(MESSAGE)).thenThrow(violation);
    when(domainEventService.isRecorded(EVENT_ID)).thenReturn(false);

    DataIntegrityViolationException thrown = assertThrows(DataIntegrityViolationException.class,
        () -> domainEventListener.onDomainEvent(MESSAGE));

    assertThat(thrown, is(sameInstance(violation)));
  }
}
