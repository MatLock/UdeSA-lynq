package com.lynq.backend.model;

import com.lynq.backend.enums.UserType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
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
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserEntity {

  @Id
  @Column(name = "id", length = 36, nullable = false)
  private String id;

  @Column(name = "full_name")
  private String fullName;

  @Enumerated(EnumType.STRING)
  @Column(name = "type", nullable = false)
  private UserType type;

  @Column(name = "profile_image_url", length = 2048)
  private String profileImageUrl;

  @Column(name = "current_position")
  private String currentPosition;

  @Column(name = "about", columnDefinition = "TEXT")
  private String about;

  @Column(name = "github_url", length = 2048)
  private String githubUrl;

  @Column(name = "linkedin_url", length = 2048)
  private String linkedinUrl;

  @Column(name = "birth_date")
  private LocalDate birthDate;

  @Column(name = "created_on", nullable = false)
  private LocalDate createdOn;

  @OneToMany(mappedBy = "user", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
  @Builder.Default
  private List<UserSkillsEntity> skills = new ArrayList<>();

  @OneToMany(mappedBy = "user", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
  @Builder.Default
  private List<UserResumeEntity> resumes = new ArrayList<>();

}