package com.lynq.analytics.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "applications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApplicationEntity {

  @Id
  @Column(name = "id", length = 36, nullable = false)
  private String id;

  @Column(name = "job_id", length = 36, nullable = false)
  private String jobId;

  @Column(name = "candidate_id", length = 36, nullable = false)
  private String candidateId;

  @Column(name = "applied_on", nullable = false)
  private LocalDate appliedOn;

  @Column(name = "lynq_score", nullable = false)
  private Integer lynqScore;

  @Column(name = "occurred_on", nullable = false)
  private Instant occurredOn;

}
