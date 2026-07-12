package com.lynq.backend.client.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.lynq.backend.enums.WorkType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Job posting sent to lynq-ml so it can extract the key technical skills.
 * The {@code work_type} property is snake_cased to match the lynq-ml
 * (pydantic) request schema.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SkillEnhanceRequest {

  private String title;
  private String description;
  @JsonProperty("work_type")
  private WorkType workType;

}