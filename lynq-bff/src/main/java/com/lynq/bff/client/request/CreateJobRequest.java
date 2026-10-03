package com.lynq.bff.client.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.lynq.bff.enums.JobPostSource;
import com.lynq.bff.enums.WorkType;
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
public class CreateJobRequest {

  private String title;
  private String description;
  private WorkType workType;
  private Integer salaryRangeDown;
  private Integer salaryRangeTop;
  private String salaryCurrency;
  private JobPostSource jobPostSource;
  private List<String> skills;
  private List<String> similarityTags;
}
