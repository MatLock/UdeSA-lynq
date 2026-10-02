package com.lynq.backend.event;

import java.time.Instant;
import java.util.UUID;

public record DomainEvent(
    UUID eventId,
    String eventType,
    String aggregateType,
    String aggregateId,
    Instant occurredOn,
    Object payload) {
}
