package com.lynq.backend.controller.request;

import com.lynq.backend.enums.WorkType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class SkillEnhanceRequest {

  @NotBlank
  private String title;
  @NotBlank
  private String description;
  @NotNull
  private WorkType workType;
}