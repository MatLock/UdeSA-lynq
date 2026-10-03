package com.lynq.analytics.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "candidate_daily_benchmark")
@IdClass(CandidateDailyBenchmarkId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CandidateDailyBenchmarkEntity {

  @Id
  @Column(name = "snapshot_on", nullable = false)
  private LocalDate snapshotOn;

  @Id
  @Column(name = "candidate_id", length = 36, nullable = false)
  private String candidateId;

  @Column(name = "market_fit")
  private Integer marketFit;

  @Column(name = "jobs_scored", nullable = false)
  private int jobsScored;

  @Column(name = "above_threshold_pct")
  private Integer aboveThresholdPct;

  @Column(name = "reach_threshold", nullable = false)
  private int reachThreshold;

  @Column(name = "peer_percentile")
  private Integer peerPercentile;

  @Column(name = "peer_group_size", nullable = false)
  private int peerGroupSize;

  @Column(name = "peer_fit_p25")
  private Integer peerFitP25;

  @Column(name = "peer_fit_median")
  private Integer peerFitMedian;

  @Column(name = "peer_fit_p75")
  private Integer peerFitP75;

  @Column(name = "skill_coverage_pct")
  private Integer skillCoveragePct;

  @Column(name = "peer_coverage_median")
  private Integer peerCoverageMedian;

  @Column(name = "computed_on", nullable = false)
  private Instant computedOn;

  @ElementCollection
  @CollectionTable(name = "candidate_daily_skill_unlocks", joinColumns = {
      @JoinColumn(name = "snapshot_on", referencedColumnName = "snapshot_on"),
      @JoinColumn(name = "candidate_id", referencedColumnName = "candidate_id")})
  @OrderBy("jobsUnlocked DESC, skill ASC")
  @Builder.Default
  private List<SkillUnlock> skillUnlocks = new ArrayList<>();

}
