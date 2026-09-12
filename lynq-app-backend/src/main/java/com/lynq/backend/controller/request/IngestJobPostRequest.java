package com.lynq.backend.controller.request;

import com.lynq.backend.enums.JobPostSource;
import com.lynq.backend.enums.WorkType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
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
public class IngestJobPostRequest {

  @NotBlank
  private String externalId;
  @NotBlank
  private String title;
  private String description;
  @NotNull
  private WorkType workType;
  @Positive
  private Integer salaryRangeDown;
  @Positive
  private Integer salaryRangeTop;
  private String jobUrl;
  @NotNull
  private JobPostSource jobPostSource;
  private String companyName;
  private Long postedAt;
  private List<String> skills;
  private List<String> similarityTags;
}
