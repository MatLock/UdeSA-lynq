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
public class JobCandidateResponse {

  private String id;
  private String userId;
  private String jobId;
  private String userFullName;
  private String userProfileImage;
  private String userCurrentPosition;
  private LocalDate userAppliedOn;
  private String userResumeUrl;
  private String userResumeName;
  private Integer lynqScore;
}
