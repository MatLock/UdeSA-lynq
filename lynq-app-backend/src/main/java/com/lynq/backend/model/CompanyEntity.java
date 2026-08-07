package com.lynq.backend.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "companies")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CompanyEntity {

  @Id
  @Column(name = "id", length = 36, nullable = false)
  private String id;

  @Column(name = "name", nullable = false, unique = true)
  private String name;

  @Column(name = "about", columnDefinition = "TEXT")
  private String about;

  @Column(name = "size")
  private Integer size;

  /** Id of the company logo in lynq-file-storage, which owns the file itself. */
  @Column(name = "lynq_file_storage_id", length = 36)
  private String lynqFileStorageId;

  @Column(name = "created_on", nullable = false)
  private LocalDate createdOn;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "owner_user_id")
  private UserEntity owner;

  @OneToMany(mappedBy = "company", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
  @Builder.Default
  private List<JobPostEntity> jobPosts = new ArrayList<>();

}