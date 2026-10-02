package com.lynq.backend.event.payload;

import java.util.List;

public record JobPostUpdatedPayload(
    String jobId,
    String title,
    String workType,
    Integer salaryRangeDown,
    Integer salaryRangeTop,
    String salaryCurrency,
    List<String> skills,
    List<String> similarityTags) {
}
