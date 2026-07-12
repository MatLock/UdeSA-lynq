package com.lynq.backend.controller.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
public class CreateUserWithCompanyRequest {

  private String userProfileImageUrl;
  @NotBlank
  private String fullName;
  @NotBlank
  private String currentPosition;
  @NotBlank
  private String userAbout;
  private String linkedinUrl;
  @NotNull
  private LocalDate birthDate;
  @NotBlank
  private String companyName;
  @NotBlank
  private String companyAbout;
  @Positive
  private Integer companySize;
  private String companyProfileImageUrl;
}
