package com.lynq.backend.repository.projection;

import java.time.LocalDate;

/**
 * Flat projection populated directly by a single JPQL constructor-expression query. It carries the
 * application id together with the applicant's public profile fields and the date the application
 * was submitted, so listing the candidates of a job never triggers lazy-loaded iterations inside a
 * transactional method. {@code userProfileImageUrl} holds the raw S3 key; the service turns it into
 * a pre-signed URL before it reaches the response. {@code jobSkills} and {@code userSkills} carry
 * the comma-separated skill names (or {@code null} when there are none), each pulled in the same
 * query via a correlated {@code group_concat} subquery, so the service can compute the LyNQ score
 * of every candidate against the job without extra round-trips.
 */
public record JobCandidateProjection(
    String id,
    String userId,
    String jobId,
    String userFullName,
    String userProfileImageUrl,
    String userCurrentPosition,
    LocalDate appliedOn,
    String jobSkills,
    String userSkills) {
}
