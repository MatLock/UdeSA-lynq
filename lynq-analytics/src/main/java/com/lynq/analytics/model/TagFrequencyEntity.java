package com.lynq.analytics.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "tag_frequency")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TagFrequencyEntity {

  @Id
  @Column(name = "tag", nullable = false)
  private String tag;

  @Column(name = "df", nullable = false)
  private int df;

  @Column(name = "weight", nullable = false)
  private double weight;

  @Column(name = "computed_on", nullable = false)
  private Instant computedOn;

}
