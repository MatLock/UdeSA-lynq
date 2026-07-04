package com.lynq.backend.controller.response;

import com.lynq.backend.enums.JobPostSource;
import com.lynq.backend.enums.WorkType;
import java.time.LocalDate;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
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
  private JobCompanyRestResponse company;
  private JobPostedByRestResponse postedBy;
  private List<String> skills;

}