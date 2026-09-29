package com.lynq.analytics.service;

import com.lynq.analytics.exceptions.InvalidDomainEventException;
import com.lynq.analytics.listener.message.DomainEventMessage;
import com.lynq.analytics.model.DomainEventEntity;
import com.lynq.analytics.repository.DomainEventRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Log4j2
public class DomainEventService {

  private static final int MAX_EVENT_TYPE_LENGTH = 64;
  private static final int MAX_AGGREGATE_TYPE_LENGTH = 32;
  private static final int MAX_AGGREGATE_ID_LENGTH = 36;

  private final DomainEventRepository domainEventRepository;
  private final List<DomainEventProjector> projectors;

  public DomainEventService(DomainEventRepository domainEventRepository,
      List<DomainEventProjector> projectors) {
    this.domainEventRepository = domainEventRepository;
    this.projectors = projectors;
  }

  @Transactional
  public boolean record(DomainEventMessage message) {
    validate(message);
    String eventId = message.eventId().toString();
    if (domainEventRepository.existsByEventId(eventId)) {
      log.info("message= Skipping already recorded domain event '{}' of type '{}'",
          eventId, message.eventType());
      return false;
    }
    domainEventRepository.saveAndFlush(DomainEventEntity.builder()
        .eventId(eventId)
        .eventType(message.eventType())
        .aggregateType(message.aggregateType())
        .aggregateId(message.aggregateId())
        .payload(message.payload().toString())
        .occurredOn(message.occurredOn())
        .receivedOn(Instant.now())
        .build());
    log.info("message= Recorded domain event '{}' of type '{}' for {} '{}'",
        eventId, message.eventType(), message.aggregateType(), message.aggregateId());
    projectors.stream()
        .filter(projector -> projector.supports(message.eventType()))
        .forEach(projector -> projector.project(message));
    return true;
  }

  @Transactional(readOnly = true)
  public boolean isRecorded(UUID eventId) {
    return eventId != null && domainEventRepository.existsByEventId(eventId.toString());
  }

  private void validate(DomainEventMessage message) {
    if (message == null) {
      throw new InvalidDomainEventException("Domain event is empty");
    }
    if (message.eventId() == null) {
      throw new InvalidDomainEventException("Domain event has no eventId");
    }
    requireText(message.eventType(), "eventType", MAX_EVENT_TYPE_LENGTH, message.eventId());
    requireText(message.aggregateType(), "aggregateType", MAX_AGGREGATE_TYPE_LENGTH,
        message.eventId());
    requireText(message.aggregateId(), "aggregateId", MAX_AGGREGATE_ID_LENGTH, message.eventId());
    if (message.occurredOn() == null) {
      throw invalid(message.eventId(), "has no occurredOn");
    }
    if (message.payload() == null || !message.payload().isObject()) {
      throw invalid(message.eventId(), "has no payload object");
    }
  }

  private void requireText(String value, String field, int maxLength, UUID eventId) {
    if (value == null || value.isBlank()) {
      throw invalid(eventId, "has no " + field);
    }
    if (value.length() > maxLength) {
      throw invalid(eventId, field + " is longer than " + maxLength + " characters");
    }
  }

  private InvalidDomainEventException invalid(UUID eventId, String problem) {
    return new InvalidDomainEventException("Domain event '" + eventId + "' " + problem);
  }
}
