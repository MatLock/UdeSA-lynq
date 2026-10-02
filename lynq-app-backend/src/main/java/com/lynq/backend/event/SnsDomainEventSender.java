package com.lynq.backend.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.lynq.backend.config.DomainEventsProperties;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.BatchResultErrorEntry;
import software.amazon.awssdk.services.sns.model.PublishBatchRequest;
import software.amazon.awssdk.services.sns.model.PublishBatchRequestEntry;
import software.amazon.awssdk.services.sns.model.PublishBatchResponse;
import software.amazon.awssdk.services.sns.model.PublishRequest;

@Component
@Log4j2
public class SnsDomainEventSender {

  static final int MAX_BATCH_SIZE = 10;

  private final SnsClient snsClient;
  private final ObjectMapper objectMapper;
  private final DomainEventsProperties properties;

  public SnsDomainEventSender(SnsClient snsClient, ObjectMapper objectMapper,
      DomainEventsProperties properties) {
    this.snsClient = snsClient;
    this.objectMapper = objectMapper.copy()
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    this.properties = properties;
  }

  public boolean send(DomainEvent event) {
    String message = serialize(event);
    if (message == null) {
      return false;
    }

    for (int attempt = 1; attempt <= attempts(); attempt++) {
      try {
        snsClient.publish(PublishRequest.builder()
            .topicArn(properties.topicArn())
            .message(message)
            .build());
        log.info("message= Published {} '{}' for {} '{}'", event.eventType(), event.eventId(),
            event.aggregateType(), event.aggregateId());
        return true;
      } catch (RuntimeException e) {
        log.warn("message= Attempt {} of {} to publish {} '{}' failed: {}", attempt, attempts(),
            event.eventType(), event.eventId(), e.getMessage());
        if (attempt < attempts() && !pause(attempt)) {
          break;
        }
      }
    }

    logLost(event);
    return false;
  }

  public List<DomainEvent> sendAll(List<DomainEvent> events) {
    List<DomainEvent> unpublished = new ArrayList<>();
    for (int from = 0; from < events.size(); from += MAX_BATCH_SIZE) {
      unpublished.addAll(
          sendBatch(events.subList(from, Math.min(from + MAX_BATCH_SIZE, events.size()))));
    }
    return unpublished;
  }

  private List<DomainEvent> sendBatch(List<DomainEvent> batch) {
    List<DomainEvent> unpublished = new ArrayList<>();
    Map<String, DomainEvent> pending = new LinkedHashMap<>();
    Map<String, String> messages = new LinkedHashMap<>();
    for (int i = 0; i < batch.size(); i++) {
      String message = serialize(batch.get(i));
      if (message == null) {
        unpublished.add(batch.get(i));
        continue;
      }
      pending.put(String.valueOf(i), batch.get(i));
      messages.put(String.valueOf(i), message);
    }

    for (int attempt = 1; attempt <= attempts() && !pending.isEmpty(); attempt++) {
      try {
        Set<String> failed = publishBatch(pending, messages);
        pending.keySet().retainAll(failed);
      } catch (RuntimeException e) {
        log.warn("message= Attempt {} of {} to publish a batch of {} domain events failed: {}",
            attempt, attempts(), pending.size(), e.getMessage());
      }
      if (!pending.isEmpty() && attempt < attempts() && !pause(attempt)) {
        break;
      }
    }

    pending.values().forEach(this::logLost);
    unpublished.addAll(pending.values());
    return unpublished;
  }

  private Set<String> publishBatch(Map<String, DomainEvent> pending, Map<String, String> messages) {
    List<PublishBatchRequestEntry> entries = new ArrayList<>();
    pending.keySet().forEach(id -> entries.add(PublishBatchRequestEntry.builder()
        .id(id)
        .message(messages.get(id))
        .build()));

    PublishBatchResponse response = snsClient.publishBatch(PublishBatchRequest.builder()
        .topicArn(properties.topicArn())
        .publishBatchRequestEntries(entries)
        .build());

    response.failed().forEach(failure -> log.warn(
        "message= SNS rejected {} '{}': {} {}", pending.get(failure.id()).eventType(),
        pending.get(failure.id()).eventId(), failure.code(), failure.message()));
    return response.failed().stream()
        .map(BatchResultErrorEntry::id)
        .collect(Collectors.toSet());
  }

  private String serialize(DomainEvent event) {
    try {
      return objectMapper.writeValueAsString(event);
    } catch (JsonProcessingException e) {
      log.error("message= {} '{}' could not be serialized and is not published: {}",
          event.eventType(), event.eventId(), e.getOriginalMessage());
      return null;
    }
  }

  private boolean pause(int attempt) {
    try {
      Thread.sleep(properties.retryBackoff().multipliedBy(attempt));
      return true;
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return false;
    }
  }

  private int attempts() {
    return Math.max(1, properties.publishAttempts());
  }

  private void logLost(DomainEvent event) {
    log.error("message= {} '{}' for {} '{}' was not published; POST /internal/events/replay "
            + "reconciles the current state", event.eventType(), event.eventId(), event.aggregateType(),
        event.aggregateId());
  }
}
