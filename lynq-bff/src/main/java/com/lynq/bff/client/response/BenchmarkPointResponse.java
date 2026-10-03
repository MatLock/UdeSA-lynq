package com.lynq.bff.client.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDate;
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
@JsonIgnoreProperties(ignoreUnknown = true)
public class BenchmarkPointResponse {

  private LocalDate snapshotOn;
  private Integer marketFit;
  private Integer aboveThresholdPct;
  private Integer peerPercentile;
  private Integer peerGroupSize;
}
