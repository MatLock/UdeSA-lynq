package com.lynq.backend.service;

import com.lynq.backend.aspect.AuditLog;
import com.lynq.backend.controller.request.UpdateUserProfileRequest;
import com.lynq.backend.exceptions.NotFoundException;
import com.lynq.backend.model.UserEntity;
import com.lynq.backend.enums.UserType;
import com.lynq.backend.repository.UserRepository;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

  private final UserRepository userRepository;

  public UserService(UserRepository userRepository){
    this.userRepository = userRepository;
  }

  @AuditLog
  @Transactional
  public UserEntity saveNewUser(String userId, UserType type, String fullName, String profileImageUrl,
      String currentPosition, String about, String githubUrl, String linkedInUrl, LocalDate birthDate) {
    UserEntity user = UserEntity.builder()
        .id(userId)
        .type(type)
        .fullName(fullName)
        .profileImageUrl(profileImageUrl)
        .currentPosition(currentPosition)
        .about(about)
        .githubUrl(githubUrl)
        .linkedinUrl(linkedInUrl)
        .birthDate(birthDate)
        .createdOn(LocalDate.now())
        .build();

    return userRepository.save(user);
  }

  @AuditLog
  @Transactional
  public UserEntity updateUserProfile(String userId, UpdateUserProfileRequest request) {
    UserEntity user = userRepository.findById(userId)
        .orElseThrow(() -> new NotFoundException("User '" + userId + "' not found"));

    if (request.getFullName() != null) {
      user.setFullName(request.getFullName());
    }
    if (request.getUserProfileImageUrl() != null) {
      user.setProfileImageUrl(request.getUserProfileImageUrl());
    }
    if (request.getCurrentPosition() != null) {
      user.setCurrentPosition(request.getCurrentPosition());
    }
    if (request.getAbout() != null) {
      user.setAbout(request.getAbout());
    }
    if (request.getGithubUrl() != null) {
      user.setGithubUrl(request.getGithubUrl());
    }
    if (request.getLinkedinUrl() != null) {
      user.setLinkedinUrl(request.getLinkedinUrl());
    }
    if (request.getBirthDate() != null) {
      user.setBirthDate(request.getBirthDate());
    }

    return userRepository.save(user);
  }

}