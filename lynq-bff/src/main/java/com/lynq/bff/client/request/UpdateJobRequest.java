package com.lynq.bff.client.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.lynq.bff.enums.JobStatus;
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
public class UpdateJobRequest {

  private String title;
  private String description;
  private WorkType workType;
  private JobStatus status;
  private Integer salaryRangeDown;
  private Integer salaryRangeTop;
  private String salaryCurrency;
  private List<String> skills;
  private List<String> similarityTags;
}
