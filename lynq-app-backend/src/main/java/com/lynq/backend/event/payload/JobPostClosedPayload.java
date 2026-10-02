package com.lynq.backend.event.payload;

import java.time.LocalDate;

public record JobPostClosedPayload(
    String jobId,
    LocalDate closedOn,
    String closeReason) {
}
