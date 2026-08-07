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
/**
 * Owner profile and company created together at registration. Neither carries an image: the profile
 * picture and the company logo are uploaded afterwards through the lynq-file-storage pre-signed URL
 * flow, which needs the user and the company to already exist.
 */
public class CreateUserWithCompanyRequest {

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
}
