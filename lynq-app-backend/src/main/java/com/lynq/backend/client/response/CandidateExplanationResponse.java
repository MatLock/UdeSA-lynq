package com.lynq.backend.client.response;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A hiring recommendation for a candidate against a job, returned by lynq-ml.
 * {@code recommendation} is a short verdict label (e.g. "hire", "no_hire",
 * "maybe"); {@code explanation} justifies it, and the two lists break the
 * reasoning into the points for and against hiring the candidate.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CandidateExplanationResponse {

  private String recommendation;
  private String explanation;
  private List<String> strengths;
  private List<String> concerns;

}
