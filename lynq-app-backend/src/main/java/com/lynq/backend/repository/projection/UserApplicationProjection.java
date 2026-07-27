package com.lynq.backend.repository.projection;

import java.time.LocalDate;

/**
 * Flat projection populated directly by a single JPQL constructor-expression query. It is the
 * candidate-facing counterpart of {@link JobCandidateProjection}: instead of describing who applied
 * to a job, it describes the job a candidate applied to, together with the owning company's public
 * fields and the date the application was submitted. Listing a candidate's own applications never
 * triggers lazy-loaded iterations inside a transactional method. {@code companyProfileImageUrl}
 * holds the raw S3 key; the service turns it into a pre-signed URL before it reaches the response.
 * {@code companyId}, {@code companyName} and {@code companyProfileImageUrl} are {@code null} for
 * scraped jobs that have no company. {@code jobSkills} carries the comma-separated job skill names
 * (or {@code null} when there are none), pulled in the same query via a correlated
 * {@code group_concat} subquery, so the service can compute the candidate's LyNQ score against
 * every job without extra round-trips. The candidate's own skills are not part of the projection:
 * they are the same for every row (always the authenticated candidate) and are read once from the
 * user entity.
 */
public record UserApplicationProjection(
    String id,
    String jobId,
    String jobTitle,
    String jobDescription,
    String companyId,
    String companyName,
    String companyProfileImageUrl,
    LocalDate appliedOn,
    String jobSkills) {
}
