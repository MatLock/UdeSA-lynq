package com.lynq.backend.event.payload;

import java.time.LocalDate;

public record JobPostReopenedPayload(
    String jobId,
    LocalDate reopenedOn) {
}
