package com.lynq.bff.client.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class UpdateUserProfileResponse {

  private String id;
  private String fullName;
  private String userProfileImageUrl;
  private String currentPosition;
  private String about;
  private String githubUrl;
  private String linkedinUrl;
  private LocalDate birthDate;
  private LocalDate createdOn;
  private Integer expectedSalary;
  private String expectedSalaryCurrency;
}
