package com.lynq.analytics.listener.message;

import java.time.LocalDate;

public record ApplicationSubmittedPayload(
    String applicationId,
    String jobId,
    String userId,
    LocalDate appliedOn,
    Integer lynqScore) {
}
