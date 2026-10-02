package com.lynq.backend.event.payload;

public record CandidateExpectedSalaryUpdatedPayload(
    String userId,
    Integer expectedSalary,
    String currency) {
}
