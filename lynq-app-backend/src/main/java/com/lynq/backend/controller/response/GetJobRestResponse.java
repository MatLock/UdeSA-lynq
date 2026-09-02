package com.lynq.backend.controller.response;

import com.lynq.backend.enums.JobPostSource;
import com.lynq.backend.enums.JobStatus;
import com.lynq.backend.enums.WorkType;
import java.time.LocalDate;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
public class GetJobRestResponse {

  private String jobId;
  private String title;
  private String description;
  private WorkType workType;
  private Integer salaryRangeDown;
  private Integer salaryRangeTop;
  private String jobUrl;
  private JobPostSource jobPostSource;
  private LocalDate createdOn;
  private Long totalSeen;
  private JobStatus jobStatus;
  private JobCompanyRestResponse company;
  private JobPostedByRestResponse postedBy;
  private List<String> skills;
  /**
   * Generalized capability tags of the post. Not meant to be displayed — they exist so the LyNQ
   * score can match a candidate with an equivalent technology — but they travel with the job so the
   * edit form can hand them back unchanged instead of dropping them.
   */
  private List<String> similarityTags;
  private Integer lynqScore;
  private Long totalCandidatesApplied;

}