package com.lynq.analytics.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SkillUnlock {

  @Column(name = "skill", nullable = false)
  private String skill;

  @Column(name = "jobs_unlocked", nullable = false)
  private int jobsUnlocked;
}
