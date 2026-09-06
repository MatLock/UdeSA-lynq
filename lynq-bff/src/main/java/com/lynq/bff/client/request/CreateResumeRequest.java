package com.lynq.bff.client.request;

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
public class CreateResumeRequest {

  private String name;
  private String language;
  private Object resume;
  private String fileId;

  /**
   * Generalized capability tags for the candidate, derived by lynq-ml. The
   * app-backend stores them against the user, not the resume: they are what a
   * job posting's own tags are matched against when the LyNQ score is computed.
   */
  private List<String> similarityTags;
}
