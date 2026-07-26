package com.lynq.backend.client.response;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The recruiter verdict plus course suggestions grouped by search query,
 * returned by lynq-ml. {@code reasons} lists the concrete gaps that keep the
 * candidate from being a perfect match — one short reason per entry — so the
 * caller can show <em>why</em> rather than a bare "not a perfect match". When
 * the candidate is a perfect match, {@code outcome} carries the fixed match
 * message and both {@code reasons} and {@code suggestions} are empty.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpskillingSuggestionResponse {

  private String outcome;
  private List<String> reasons;
  private List<QuerySuggestion> suggestions;

}
