package com.lynq.bff.client.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.lynq.bff.enums.JobPostSource;
import com.lynq.bff.enums.JobStatus;
import com.lynq.bff.enums.WorkType;
import java.time.LocalDate;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class JobDetailsResponse {

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
  private JobCompanyResponse company;
  private JobPostedByResponse postedBy;
  private List<String> skills;
  private List<String> similarityTags;
  private Integer lynqScore;
  private Long totalCandidatesApplied;
  private boolean alreadyApplied;
}
