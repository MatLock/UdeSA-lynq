package com.lynq.backend.repository.projection;

import java.time.LocalDate;

public record JobCandidateProjection(
    String id,
    String userId,
    String jobId,
    String userFullName,
    String userFileStorageId,
    String userCurrentPosition,
    LocalDate appliedOn,
    String jobSkills,
    String userSkills,
    String jobSimilarityTags,
    String userSimilarityTags) {
}
