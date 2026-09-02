package com.lynq.bff.controller.request;

import com.lynq.bff.enums.ResumeTemplate;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PreviewResumeRequest {

  private Object resume;
  private ResumeTemplate template;
}
