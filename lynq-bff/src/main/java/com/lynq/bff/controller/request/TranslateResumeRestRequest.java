package com.lynq.bff.controller.request;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TranslateResumeRestRequest {

  /** Language to translate the resume into, as a supported_languages code (e.g. "FR"). */
  private String language;
}
