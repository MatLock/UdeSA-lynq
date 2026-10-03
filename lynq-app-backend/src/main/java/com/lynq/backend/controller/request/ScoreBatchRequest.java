package com.lynq.backend.controller.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
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
public class ScoreBatchRequest {

  public static final int MAX_PROFILES = 5000;
  public static final int MAX_PAIRS = 5000;

  @NotEmpty
  @Size(max = MAX_PROFILES)
  @Valid
  private List<ScoreBatchProfileRequest> jobPosts;

  @NotEmpty
  @Size(max = MAX_PROFILES)
  @Valid
  private List<ScoreBatchProfileRequest> candidates;

  @NotEmpty
  @Size(max = MAX_PAIRS)
  @Valid
  private List<ScoreBatchPairRequest> pairs;
}
