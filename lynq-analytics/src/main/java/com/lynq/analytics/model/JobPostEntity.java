package com.lynq.analytics.model;

import com.lynq.analytics.enums.JobStatus;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "job_posts")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobPostEntity {

  @Id
  @Column(name = "id", length = 36, nullable = false)
  private String id;

  @Column(name = "title", nullable = false)
  private String title;

  @Column(name = "category", length = 64)
  private String category;

  @Column(name = "work_type", length = 32, nullable = false)
  private String workType;

  @Column(name = "source", length = 32, nullable = false)
  private String source;

  @Column(name = "company_id", length = 36)
  private String companyId;

  @Column(name = "created_by_user_id", length = 36)
  private String createdByUserId;

  @Column(name = "salary_range_down")
  private Integer salaryRangeDown;

  @Column(name = "salary_range_top")
  private Integer salaryRangeTop;

  @Column(name = "salary_currency", length = 3)
  private String salaryCurrency;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", length = 16, nullable = false)
  private JobStatus status;

  @Column(name = "published_on", nullable = false)
  private LocalDate publishedOn;

  @Column(name = "closed_on")
  private LocalDate closedOn;

  @Column(name = "close_reason", length = 32)
  private String closeReason;

  @Column(name = "reopened_on")
  private LocalDate reopenedOn;

  @Column(name = "details_occurred_on", nullable = false)
  private Instant detailsOccurredOn;

  @Column(name = "status_occurred_on", nullable = false)
  private Instant statusOccurredOn;

  @ElementCollection
  @CollectionTable(name = "job_post_skills", joinColumns = @JoinColumn(name = "job_id"))
  @Column(name = "skill", nullable = false)
  @Builder.Default
  private Set<String> skills = new HashSet<>();

  @ElementCollection
  @CollectionTable(name = "job_post_tags", joinColumns = @JoinColumn(name = "job_id"))
  @Column(name = "tag", nullable = false)
  @Builder.Default
  private Set<String> tags = new HashSet<>();

}
