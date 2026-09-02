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
 * A generalized capability tag of a candidate ("Asynchronous Messaging"), as opposed to the literal
 * skill they wrote down ("RabbitMQ"). Kept in its own table rather than alongside
 * {@link UserSkillsEntity} because the two are shown differently: skills are the chips the profile
 * displays, tags exist only so a candidate still matches a job that asks for an equivalent
 * technology. lynq-ml derives them when the resume's skills are extracted.
 */
@Entity
@Table(name = "user_similarity_tags", uniqueConstraints = @UniqueConstraint(
    name = "uq_user_similarity_tags", columnNames = {"user_id", "similarity_tag"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserSimilarityTagEntity {

  @Id
  @Column(name = "id", length = 36, nullable = false)
  private String id;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "user_id", nullable = false)
  private UserEntity user;

  @Column(name = "similarity_tag", nullable = false)
  private String similarityTag;

}
