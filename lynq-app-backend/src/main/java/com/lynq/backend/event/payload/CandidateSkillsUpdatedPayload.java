package com.lynq.backend.event.payload;

import java.util.List;

public record CandidateSkillsUpdatedPayload(
    String userId,
    List<String> skills,
    List<String> similarityTags) {
}
