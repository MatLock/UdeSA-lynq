package com.lynq.backend.repository.projection;

import com.lynq.backend.enums.JobPostSource;
import com.lynq.backend.enums.JobStatus;
import com.lynq.backend.enums.WorkType;
import java.time.LocalDate;

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
    JobStatus jobStatus,
    String companyId,
    String companyName,
    String companyAbout,
    Integer companySize,
    String companyFileStorageId,
    String userId,
    String userFullName,
    String userFileStorageId,
    String userCurrentPosition,
    String skills,
    String similarityTags) {
}