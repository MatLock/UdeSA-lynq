package com.lynq.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "user_application_job", uniqueConstraints = @UniqueConstraint(
    name = "uq_user_application_job", columnNames = {"job_post_id", "user_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserApplicationJobEntity {

  @Id
  @Column(name = "id", length = 36, nullable = false)
  private String id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "job_post_id", nullable = false)
  private JobPostEntity jobPost;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private UserEntity user;

  @Column(name = "applied_on", nullable = false)
  private LocalDate appliedOn;

}