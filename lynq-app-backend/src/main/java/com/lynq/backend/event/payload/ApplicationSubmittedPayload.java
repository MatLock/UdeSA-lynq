package com.lynq.backend.event.payload;

import java.time.LocalDate;

public record ApplicationSubmittedPayload(
    String applicationId,
    String jobId,
    String userId,
    LocalDate appliedOn,
    Integer lynqScore) {
}
