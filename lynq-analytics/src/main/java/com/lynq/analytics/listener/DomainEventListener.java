package com.lynq.analytics.listener;

import com.lynq.analytics.listener.message.DomainEventMessage;
import com.lynq.analytics.service.DomainEventService;
import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.extern.log4j.Log4j2;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

@Component
@Log4j2
public class DomainEventListener {

  private final DomainEventService domainEventService;

  public DomainEventListener(DomainEventService domainEventService) {
    this.domainEventService = domainEventService;
  }

  @SqsListener("${lynq.analytics.events.queue}")
  public void onDomainEvent(DomainEventMessage message) {
    try {
      domainEventService.record(message);
    } catch (DataIntegrityViolationException e) {
      if (!domainEventService.isRecorded(message.eventId())) {
        throw e;
      }
      log.info("message= Domain event '{}' was recorded concurrently, acknowledging the duplicate",
          message.eventId());
    }
  }
}
