package com.lynq.analytics.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "job_daily_stats")
@IdClass(JobDailyStatsId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobDailyStatsEntity {

  @Id
  @Column(name = "snapshot_on", nullable = false)
  private LocalDate snapshotOn;

  @Id
  @Column(name = "category", length = 64, nullable = false)
  private String category;

  @Column(name = "open_job_posts", nullable = false)
  private int openJobPosts;

  @Column(name = "open_with_salary", nullable = false)
  private int openWithSalary;

}
