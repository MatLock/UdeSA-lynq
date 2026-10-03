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
@Table(name = "skill_daily_demand")
@IdClass(SkillDailyDemandId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SkillDailyDemandEntity {

  @Id
  @Column(name = "snapshot_on", nullable = false)
  private LocalDate snapshotOn;

  @Id
  @Column(name = "skill", nullable = false)
  private String skill;

  @Column(name = "open_job_posts", nullable = false)
  private int openJobPosts;

}
