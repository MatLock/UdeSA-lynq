package com.lynq.bff.client.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDate;
import java.util.List;
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
public class GetCompanyDetailResponse {

  private String id;
  private String name;
  private String about;
  private Integer size;
  private String profileImageUrl;
  private LocalDate createdOn;
  private List<CompanyJobResponse> jobs;
}
