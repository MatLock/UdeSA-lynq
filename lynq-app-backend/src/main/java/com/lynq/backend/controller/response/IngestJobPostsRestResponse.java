package com.lynq.backend.controller.response;

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
public class IngestJobPostsRestResponse {

  private int jobs;
  private int companies;
  private int skills;
  private int similarityTags;
  private int skipped;
}
