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

  /**
   * Whether the candidate asking for these details has already applied. Always
   * {@code false} for a company viewer, which has no applications of its own.
   * The apply action is disabled on it, so it is never null: an absent flag would
   * read as "not applied" and offer an action that can only fail.
   */
  private boolean alreadyApplied;

  public static GetJobDetailForCandidateRestResponse from(GetJobRestResponse source,
      Long totalCandidatesApplied, boolean alreadyApplied) {
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
        .jobStatus(source.getJobStatus())
        .company(source.getCompany())
        .postedBy(source.getPostedBy())
        .skills(source.getSkills())
        .similarityTags(source.getSimilarityTags())
        .lynqScore(source.getLynqScore())
        .totalCandidatesApplied(totalCandidatesApplied)
        .alreadyApplied(alreadyApplied)
        .build();
  }

}