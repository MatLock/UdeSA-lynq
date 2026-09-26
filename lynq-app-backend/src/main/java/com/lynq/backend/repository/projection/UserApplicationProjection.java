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
    String companyLogoUrl,
    String resumeFileStorageId,
    String resumeName,
    LocalDate appliedOn,
    String jobSkills,
    String jobSimilarityTags) {
}
