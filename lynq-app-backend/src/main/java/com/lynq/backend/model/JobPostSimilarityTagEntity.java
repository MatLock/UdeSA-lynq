package com.lynq.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A generalized capability tag of a job post ("Asynchronous Messaging"), the counterpart of
 * {@link UserSimilarityTagEntity}. Kept out of {@link JobPostSkillEntity} because the skills are the chips the
 * job card shows, while the tags exist only to widen the LyNQ score: a post asking for Kafka and a
 * candidate who only used RabbitMQ meet here. lynq-ml derives them alongside the suggested skills.
 */
@Entity
@Table(name = "job_post_similarity_tags", uniqueConstraints = @UniqueConstraint(
    name = "uq_job_post_similarity_tags", columnNames = {"job_id", "similarity_tag"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobPostSimilarityTagEntity {

  @Id
  @Column(name = "id", length = 36, nullable = false)
  private String id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "job_id", nullable = false)
  private JobPostEntity jobPost;

  @Column(name = "similarity_tag", nullable = false)
  private String similarityTag;

}
