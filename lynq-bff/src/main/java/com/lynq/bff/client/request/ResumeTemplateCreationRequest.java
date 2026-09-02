package com.lynq.bff.client.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.lynq.bff.enums.ResumeTemplate;
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
public class ResumeTemplateCreationRequest {

  @JsonProperty("resume_content")
  private Object resumeContent;

  @JsonProperty("profile_url")
  private String profileUrl;

  @JsonProperty("put_resume_url")
  private String putResumeUrl;

  private ResumeTemplate template;
}
