package com.lynq.analytics.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "candidates")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CandidateEntity {

  @Id
  @Column(name = "id", length = 36, nullable = false)
  private String id;

  @Column(name = "expected_salary")
  private Integer expectedSalary;

  @Column(name = "expected_salary_currency", length = 3)
  private String expectedSalaryCurrency;

  @Column(name = "synthetic", nullable = false)
  private boolean synthetic;

  @Column(name = "skills_occurred_on")
  private Instant skillsOccurredOn;

  @Column(name = "salary_occurred_on")
  private Instant salaryOccurredOn;

  @ElementCollection
  @CollectionTable(name = "candidate_skills", joinColumns = @JoinColumn(name = "candidate_id"))
  @Column(name = "skill", nullable = false)
  @Builder.Default
  private Set<String> skills = new HashSet<>();

  @ElementCollection
  @CollectionTable(name = "candidate_tags", joinColumns = @JoinColumn(name = "candidate_id"))
  @Column(name = "tag", nullable = false)
  @Builder.Default
  private Set<String> tags = new HashSet<>();

}
