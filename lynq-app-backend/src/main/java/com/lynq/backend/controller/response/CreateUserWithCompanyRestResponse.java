package com.lynq.backend.controller.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CreateUserWithCompanyRestResponse {

  private String companyId;
  private String companyName;
  private String companyAbout;
  private Integer companySize;
  private LocalDate companyCreatedOn;
  private String ownerUserId;

}