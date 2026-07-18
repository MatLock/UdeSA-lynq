package com.lynq.backend.controller.response;

import com.lynq.backend.enums.JobStatus;
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
public class RefreshJobRestResponse {

  private String jobId;
  private JobStatus jobStatus;
  private LocalDate createdOn;

}