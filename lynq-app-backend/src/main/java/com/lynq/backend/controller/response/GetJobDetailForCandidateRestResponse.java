package com.lynq.backend.controller.response;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@NoArgsConstructor
@SuperBuilder
public class GetJobDetailForCandidateRestResponse extends GetJobRestResponse {

  private Long totalCandidatesApplied;

  public static GetJobDetailForCandidateRestResponse from(GetJobRestResponse source,
      Long totalCandidatesApplied) {
    return GetJobDetailForCandidateRestResponse.builder()
        .jobId(source.getJobId())
        .title(source.getTitle())
        .description(source.getDescription())
        .workType(source.getWorkType())
        .salaryRangeDown(source.getSalaryRangeDown())
        .salaryRangeTop(source.getSalaryRangeTop())
        .jobUrl(source.getJobUrl())
        .jobPostSource(source.getJobPostSource())
        .createdOn(source.getCreatedOn())
        .totalSeen(source.getTotalSeen())
        .company(source.getCompany())
        .postedBy(source.getPostedBy())
        .skills(source.getSkills())
        .lynqScore(source.getLynqScore())
        .totalCandidatesApplied(totalCandidatesApplied)
        .build();
  }

}