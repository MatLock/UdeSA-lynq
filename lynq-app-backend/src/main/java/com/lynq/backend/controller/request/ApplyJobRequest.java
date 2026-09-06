package com.lynq.backend.controller.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ApplyJobRequest {

  /**
   * The resume the candidate applies with. Required: an application whose
   * resume the recruiter cannot open is not worth registering, and the caller
   * always has one to pick — the UI only offers the action once it has listed
   * them.
   */
  @NotBlank
  private String resumeId;

}
