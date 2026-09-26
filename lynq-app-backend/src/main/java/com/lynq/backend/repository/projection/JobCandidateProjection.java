package com.lynq.backend.repository.projection;

import java.time.LocalDate;

public record JobCandidateProjection(
    String id,
    String userId,
    String jobId,
    String userFullName,
    String userFileStorageId,
    String userCurrentPosition,
    // The document the candidate applied with. It hangs off the application, not
    // off the candidate's resumes, because a CV Tailor resume is never stored as
    // one of those.
    String userResumeFileStorageId,
    String userResumeName,
    LocalDate appliedOn,
    String jobSkills,
    String userSkills,
    String jobSimilarityTags,
    String userSimilarityTags) {
}
