package com.lynq.backend.repository.projection;

import java.time.LocalDate;

public record UserApplicationProjection(
    String id,
    String jobId,
    String jobTitle,
    String jobDescription,
    String companyId,
    String companyName,
    String companyFileStorageId,
    LocalDate appliedOn,
    String jobSkills,
    String jobSimilarityTags) {
}
