package com.lynq.iam.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserEntity {

  @Id
  @Column(name = "id", nullable = false, length = 36)
  private String id;

  @Column(name = "email", nullable = false, length = 100)
  private String email;

  @Column(name = "username", nullable = false, length = 20)
  private String username;

  @Column(name = "password", nullable = false, length = 60)
  private String password;

  @Column(name = "creation_date", nullable = false, updatable = false)
  private LocalDateTime creationDate;
}