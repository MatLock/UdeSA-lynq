package com.lynq.analytics.listener.message;

import java.util.List;

public record CandidateSkillsUpdatedPayload(
    String userId,
    List<String> skills,
    List<String> similarityTags,
    Boolean synthetic) {
}
