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

  /**
   * The tailored PDF the application carries. It lives in lynq-file-storage and
   * is never one of the candidate's stored resumes, so this is the only handle
   * on it: the browser downloads it from here when the posting is external.
   */
  private String resumeFileId;
  private String resumePdfUrl;
}
