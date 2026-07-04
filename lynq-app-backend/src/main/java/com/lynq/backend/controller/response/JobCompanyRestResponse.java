package com.lynq.backend.controller.response;

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
public class JobCompanyRestResponse {

  private String id;
  private String name;
  private String about;
  private Integer size;
  private String profileImageUrl;

}