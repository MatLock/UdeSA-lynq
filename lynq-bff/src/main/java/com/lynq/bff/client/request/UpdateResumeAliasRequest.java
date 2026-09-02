package com.lynq.bff.client.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Body of lynq-app-backend's PUT /dmz/user/resume/{resumeId}/alias. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateResumeAliasRequest {

  private String alias;
}
