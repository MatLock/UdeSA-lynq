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
public class JobPostedByRestResponse {

  private String id;
  private String fullName;
  private String profileImageUrl;
  private String currentPosition;

}