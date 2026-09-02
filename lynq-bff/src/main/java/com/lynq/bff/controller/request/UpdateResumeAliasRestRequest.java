package com.lynq.bff.controller.request;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Body of PUT /resume/{resumeId}/alias: the alias the candidate wants the resume known by. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UpdateResumeAliasRestRequest {

  private String alias;
}
