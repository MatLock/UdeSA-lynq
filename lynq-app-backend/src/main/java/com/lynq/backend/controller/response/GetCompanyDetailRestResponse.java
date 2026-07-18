package com.lynq.backend.controller.response;

import java.time.LocalDate;
import java.util.List;
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
public class GetCompanyDetailRestResponse {

  private String id;
  private String name;
  private String about;
  private Integer size;
  private String profileImageUrl;
  private LocalDate createdOn;
  private List<CompanyJobRestResponse> jobs;

}