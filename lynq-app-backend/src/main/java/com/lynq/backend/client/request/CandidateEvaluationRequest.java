package com.lynq.backend.client.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A job + candidate pair sent to lynq-ml. This is the shared request payload for
 * both the upskilling-suggestion and candidate-explanation endpoints, and
 * serializes to the {@code {"job": ..., "candidate": ...}} JSON structure their
 * prompt templates expect.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CandidateEvaluationRequest {

  private JobSpec job;
  private CandidateSpec candidate;

}
