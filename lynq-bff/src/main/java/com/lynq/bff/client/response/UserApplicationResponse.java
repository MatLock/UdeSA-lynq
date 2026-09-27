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
  private String resumeName;
  private String resumePdfUrl;
}
