package com.lynq.backend.repository.projection;

import java.time.LocalDate;

public record JobCandidateProjection(
    String id,
    String userId,
    String jobId,
    String userFullName,
    String userFileStorageId,
    String userCurrentPosition,
    // Null for applications registered before candidates chose a resume, and
    // for a resume whose document was never stored.
    String userResumeFileStorageId,
    LocalDate appliedOn,
    String jobSkills,
    String userSkills,
    String jobSimilarityTags,
    String userSimilarityTags) {
}
