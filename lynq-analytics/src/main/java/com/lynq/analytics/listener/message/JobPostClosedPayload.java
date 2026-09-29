package com.lynq.analytics.listener.message;

import java.time.LocalDate;

public record JobPostClosedPayload(
    String jobId,
    LocalDate closedOn,
    String closeReason) {
}
