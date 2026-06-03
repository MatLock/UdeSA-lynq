package com.lynq.iam.repository;

import com.lynq.iam.model.UserEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<UserEntity, String> {

  boolean existsByUsername(String username);

  boolean existsByEmail(String email);

  Optional<UserEntity> findByUsername(String username);

  Optional<UserEntity> findByEmail(String email);
}