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
@Table(name = "category_daily_salary")
@IdClass(CategoryDailySalaryId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CategoryDailySalaryEntity {

  @Id
  @Column(name = "snapshot_on", nullable = false)
  private LocalDate snapshotOn;

  @Id
  @Column(name = "category", length = 64, nullable = false)
  private String category;

  @Id
  @Column(name = "work_type", length = 32, nullable = false)
  private String workType;

  @Id
  @Column(name = "currency", length = 3, nullable = false)
  private String currency;

  @Column(name = "n", nullable = false)
  private int n;

  @Column(name = "median")
  private Double median;

  @Column(name = "p25")
  private Double p25;

  @Column(name = "p75")
  private Double p75;

}
