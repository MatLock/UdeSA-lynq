package com.lynq.bff.controller.response;

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
public class ResumeTailorApplyRestResponse {

  private Object application;
  private boolean alreadyApplied;
  private String conversationStatus;
}
