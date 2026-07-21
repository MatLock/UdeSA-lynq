package com.lynq.backend.client.response;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One missing competency and the courses lynq-ml found to address it.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuerySuggestion {

  private String query;
  private List<Course> courses;

}
