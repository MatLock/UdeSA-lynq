package com.lynq.backend.controller.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class IngestJobPostsRequest {

  @NotEmpty
  @Valid
  private List<IngestJobPostRequest> jobPosts;
}
