package com.lynq.backend.controller.request;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
public class UpdateUserProfileRequest {

  private String fullName;
  private String userProfileImageUrl;
  private String currentPosition;
  private String about;
  private String githubUrl;
  private String linkedinUrl;
  private LocalDate birthDate;

}