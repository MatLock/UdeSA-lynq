package com.lynq.backend.controller.response;

import com.lynq.backend.enums.JobStatus;
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
public class UserProfileJobRestResponse {

  private String id;
  private String title;
  private String description;
  private JobStatus jobStatus;

}