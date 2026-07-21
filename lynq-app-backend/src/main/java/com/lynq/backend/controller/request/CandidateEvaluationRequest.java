package com.lynq.backend.controller.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Inbound job + candidate payload shared by the upskilling-suggestion and
 * candidate-explanation proxy endpoints. It mirrors the request schema expected
 * by lynq-ml.
 */
@Getter
@Setter
@NoArgsConstructor
public class CandidateEvaluationRequest {

  @NotNull
  @Valid
  private JobSpecRequest job;
  @NotNull
  @Valid
  private CandidateSpecRequest candidate;
}
