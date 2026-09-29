package com.lynq.analytics.listener.message;

import java.time.LocalDate;

public record JobPostReopenedPayload(
    String jobId,
    LocalDate reopenedOn) {
}
