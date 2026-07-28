package com.lynq.iam.service;

import com.lynq.iam.aspect.AuditLog;
import com.lynq.iam.model.UserEntity;
import com.lynq.iam.repository.UserRepository;
import jakarta.transaction.Transactional;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import com.fasterxml.uuid.Generators;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Service
public class UserService {

  private final UserRepository userRepository;
  private final BCryptPasswordEncoder passwordEncoder;

  public UserService(UserRepository userRepository, BCryptPasswordEncoder passwordEncoder) {
    this.userRepository = userRepository;
    this.passwordEncoder = passwordEncoder;
  }

  @Transactional
  @AuditLog
  public UserEntity createUser(String username, String password, String email) {
    validateUniqueness(username, email);

    UserEntity user = UserEntity.builder()
        .id(Generators.timeBasedEpochGenerator().generate().toString())
        .username(username)
        .email(email)
        .password(passwordEncoder.encode(password))
        .creationDate(LocalDateTime.now(ZoneOffset.UTC))
        .build();

    return userRepository.save(user);
  }

  @Transactional
  @AuditLog
  public void updatePassword(String userId, String password) {
    UserEntity user = userRepository.findById(userId)
        .orElseThrow(() -> new IllegalArgumentException("User not found"));
    user.setPassword(passwordEncoder.encode(password));
    userRepository.save(user);
  }

  private void validateUniqueness(String username, String email) {
    if (userRepository.existsByUsername(username)) {
      throw new IllegalArgumentException("Username already exists");
    }
    if (userRepository.existsByEmail(email)) {
      throw new IllegalArgumentException("Email already exists");
    }
  }
}
