package com.lynq.backend.client.response;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The recruiter verdict plus course suggestions grouped by search query,
 * returned by lynq-ml. When the candidate is a perfect match, {@code outcome}
 * carries the fixed match message and {@code suggestions} is empty.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpskillingSuggestionResponse {

  private String outcome;
  private List<QuerySuggestion> suggestions;

}
