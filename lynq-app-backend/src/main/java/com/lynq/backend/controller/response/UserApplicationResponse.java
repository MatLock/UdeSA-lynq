package com.lynq.backend.controller.response;

import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UserApplicationResponse {

  private String id;
  private String jobId;
  private String jobTitle;
  private String jobDescription;
  private String companyId;
  private String companyName;
  private String companyProfileImage;
  private LocalDate appliedOn;
  private Integer lynqScore;

}