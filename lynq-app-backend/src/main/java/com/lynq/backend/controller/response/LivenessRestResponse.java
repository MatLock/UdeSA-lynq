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
public class LivenessRestResponse {

  private int alive;
  private int closed;
  private int gone;
  private int unknown;
  private int skipped;
}
