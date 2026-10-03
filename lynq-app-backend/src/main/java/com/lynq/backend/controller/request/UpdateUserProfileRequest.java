package com.lynq.backend.controller.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
public class UpdateUserProfileRequest {

  private String fullName;
  private String currentPosition;
  private String about;
  private String githubUrl;
  private String linkedinUrl;
  private LocalDate birthDate;
  @Positive
  private Integer expectedSalary;
  @Pattern(regexp = "ARS|USD")
  private String expectedSalaryCurrency;

}