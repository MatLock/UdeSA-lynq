package com.lynq.backend.controller.request;

import com.lynq.backend.enums.JobStatus;
import com.lynq.backend.enums.WorkType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class UpdateJobRequest {

  @NotBlank
  private String title;
  @NotBlank
  private String description;
  @NotNull
  private WorkType workType;
  @NotNull
  private JobStatus status;
  @Positive
  private Integer salaryRangeDown;
  @Positive
  private Integer salaryRangeTop;
  private List<String> skills;
}