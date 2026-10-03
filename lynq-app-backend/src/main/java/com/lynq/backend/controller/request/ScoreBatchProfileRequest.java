package com.lynq.backend.controller.request;

import jakarta.validation.constraints.NotBlank;
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
public class ScoreBatchProfileRequest {

  @NotBlank
  private String id;
  private List<String> skills;
  private List<String> similarityTags;
}
