package com.lynq.backend.controller.response;

import java.util.Map;
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
public class ReplayEventsRestResponse {

  private int published;
  private int failed;
  private int skippedJobPosts;
  private Map<String, Integer> publishedByEventType;
}
