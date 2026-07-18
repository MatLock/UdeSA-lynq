package com.lynq.backend.model;

import com.lynq.backend.enums.JobPostSource;
import com.lynq.backend.enums.JobStatus;
import com.lynq.backend.enums.WorkType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
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

  @Column(name = "description", columnDefinition = "TEXT")
  private String description;

  @Enumerated(EnumType.STRING)
  @Column(name = "work_type", nullable = false)
  private WorkType workType;

  @Column(name = "salary_range_down")
  private Integer salaryRangeDown;

  @Column(name = "salary_range_top")
  private Integer salaryRangeTop;

  @Column(name = "job_url", length = 2048)
  private String jobUrl;

  @Column(name = "created_on", nullable = false)
  private LocalDate createdOn;

  @Column(name = "closed_on")
  private LocalDate closedOn;

  @Column(name = "total_seen", nullable = false)
  @Builder.Default
  private Long totalSeen = 0L;

  @Enumerated(EnumType.STRING)
  @Column(name = "job_status", nullable = false)
  @Builder.Default
  private JobStatus jobStatus = JobStatus.OPEN;

  @Enumerated(EnumType.STRING)
  @Column(name = "job_post_source", nullable = false)
  private JobPostSource jobPostSource;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "created_by_user_id")
  private UserEntity createdByUser;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "company_id")
  private CompanyEntity company;

  @OneToMany(mappedBy = "jobPost", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
  @Builder.Default
  private List<JobPostSkillEntity> skills = new ArrayList<>();

}