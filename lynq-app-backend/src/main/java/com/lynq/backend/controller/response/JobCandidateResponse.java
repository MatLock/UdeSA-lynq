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
public class JobCandidateResponse {

  private String id;
  private String userId;
  private String jobId;
  private String userFullName;
  private String userProfileImage;
  private String userCurrentPosition;
  private LocalDate userAppliedOn;
  private Integer lynqScore;

}
