package com.lynq.analytics.service;

import com.lynq.analytics.exceptions.InvalidDomainEventException;
import com.lynq.analytics.listener.message.DomainEventMessage;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

final class ProjectionSupport {

  private ProjectionSupport() {
  }

  static <T> T read(ObjectMapper objectMapper, DomainEventMessage message, Class<T> type) {
    try {
      return objectMapper.treeToValue(message.payload(), type);
    } catch (JacksonException e) {
      throw invalid(message, "has a payload that cannot be read: " + e.getOriginalMessage());
    }
  }

  static void requireAggregateId(DomainEventMessage message, String id, String field) {
    requireText(message, id, field);
    if (!id.equals(message.aggregateId())) {
      throw invalid(message, "has " + field + " '" + id + "' but aggregateId '"
          + message.aggregateId() + "'");
    }
  }

  static void requireText(DomainEventMessage message, String value, String field) {
    if (value == null || value.isBlank()) {
      throw invalid(message, "has no " + field);
    }
  }

  static void requirePresent(DomainEventMessage message, Object value, String field) {
    if (value == null) {
      throw invalid(message, "has no " + field);
    }
  }

  static boolean isNotBefore(Instant occurredOn, Instant storedOn) {
    return storedOn == null || !occurredOn.isBefore(storedOn);
  }

  static void replace(Set<String> current, List<String> values) {
    current.clear();
    if (values == null) {
      return;
    }
    Set<String> seen = new HashSet<>();
    values.stream()
        .filter(Objects::nonNull)
        .map(String::trim)
        .filter(value -> !value.isEmpty())
        .filter(value -> seen.add(value.toLowerCase(Locale.ROOT)))
        .forEach(current::add);
  }

  static InvalidDomainEventException invalid(DomainEventMessage message, String problem) {
    return new InvalidDomainEventException(
        "Domain event '" + message.eventId() + "' of type '" + message.eventType() + "' "
            + problem);
  }
}
