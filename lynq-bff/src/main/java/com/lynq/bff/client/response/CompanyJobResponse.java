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
public class CompanyJobResponse {

  private String jobId;
  private String title;
  private String status;
  private LocalDate publishedOn;
  private Integer applications;
  private Double medianScore;
  private boolean insufficientData;
}
