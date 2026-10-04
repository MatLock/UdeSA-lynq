package com.lynq.backend.controller.response;

import com.lynq.backend.enums.JobPostSource;
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
public class VerificationCandidateRestResponse {

  private String id;
  private String jobUrl;
  private JobPostSource source;
  private String category;
  private LocalDate lastSeenOn;
  private LocalDate lastCheckedOn;
}
