package com.lynq.backend.repository.projection;

import com.lynq.backend.enums.JobPostSource;
import com.lynq.backend.enums.WorkType;
import java.time.LocalDate;

/**
 * Flat projection populated directly by a single JPQL constructor-expression query. It carries the
 * job fields together with the owning company and the user who created the post, so listing
 * available jobs never triggers lazy-loaded iterations inside a transactional method. Skills are
 * pulled in the same query via a correlated {@code group_concat} subquery: {@code skills} holds the
 * comma-separated skill names (or {@code null} when the job has none).
 */
public record JobWithDetailsProjection(
    String jobId,
    String title,
    String description,
    WorkType workType,
    Integer salaryRangeDown,
    Integer salaryRangeTop,
    String jobUrl,
    JobPostSource jobPostSource,
    LocalDate createdOn,
    Long totalSeen,
    String companyId,
    String companyName,
    String companyAbout,
    Integer companySize,
    String companyProfileImageUrl,
    String userId,
    String userFullName,
    String userProfileImageUrl,
    String userCurrentPosition,
    String skills) {
}