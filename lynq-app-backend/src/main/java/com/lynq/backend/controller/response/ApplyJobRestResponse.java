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
public class ApplyJobRestResponse {

  private String applicationId;
  private String jobId;
  private String userId;
  private LocalDate appliedOn;

}
