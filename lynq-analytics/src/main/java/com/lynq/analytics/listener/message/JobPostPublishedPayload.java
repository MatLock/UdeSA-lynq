package com.lynq.analytics.listener.message;

import java.time.LocalDate;
import java.util.List;

public record JobPostPublishedPayload(
    String jobId,
    String title,
    String category,
    String workType,
    String source,
    String companyId,
    String createdByUserId,
    Integer salaryRangeDown,
    Integer salaryRangeTop,
    String salaryCurrency,
    List<String> skills,
    List<String> similarityTags,
    LocalDate publishedOn) {
}
