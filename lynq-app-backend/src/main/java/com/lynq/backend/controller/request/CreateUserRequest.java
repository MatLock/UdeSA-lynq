package com.lynq.backend.controller.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
public class CreateUserRequest {

  @NotBlank
  private String fullName;
  private String currentPosition;
  private String about;
  private String githubUrl;
  private String linkedinUrl;
  @NotNull
  private LocalDate birthDate;




}
