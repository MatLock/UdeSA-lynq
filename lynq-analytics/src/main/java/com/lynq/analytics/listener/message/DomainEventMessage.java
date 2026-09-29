package com.lynq.analytics.listener.message;

import java.time.Instant;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

public record DomainEventMessage(
    UUID eventId,
    String eventType,
    String aggregateType,
    String aggregateId,
    Instant occurredOn,
    JsonNode payload) {
}
