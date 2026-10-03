package com.lynq.bff.client.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDate;
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
@JsonIgnoreProperties(ignoreUnknown = true)
public class CandidateBenchmarkResponse {

  private LocalDate snapshotOn;
  private Integer marketFit;
  private Integer jobsScored;
  private Integer aboveThresholdPct;
  private Integer reachThreshold;
  private Integer peerPercentile;
  private Integer peerGroupSize;
  private Integer peerFitP25;
  private Integer peerFitMedian;
  private Integer peerFitP75;
  private Integer skillCoveragePct;
  private Integer peerCoverageMedian;
  private List<SkillUnlockResponse> skillUnlocks;
  private List<BenchmarkPointResponse> series;
}
