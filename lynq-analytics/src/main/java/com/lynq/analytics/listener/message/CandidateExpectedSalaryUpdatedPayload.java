package com.lynq.analytics.listener.message;

public record CandidateExpectedSalaryUpdatedPayload(
    String userId,
    Integer expectedSalary,
    String currency) {
}
