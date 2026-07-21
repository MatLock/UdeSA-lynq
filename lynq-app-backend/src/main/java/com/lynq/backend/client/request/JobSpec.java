package com.lynq.backend.client.request;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The job a candidate is being evaluated against, as sent to lynq-ml. The field
 * names already match the lynq-ml (pydantic) schema, so no snake_case remapping
 * is required.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobSpec {

  private String description;
  private List<String> skills;

}
