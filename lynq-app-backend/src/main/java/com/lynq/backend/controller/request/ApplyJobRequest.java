package com.lynq.backend.controller.request;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ApplyJobRequest {

  /**
   * The candidate's own resume to apply with. The document and the label are
   * read off it, so the caller never says which file to attach.
   */
  private String resumeId;

  /**
   * A document the candidate applies with that is not one of their stored
   * resumes — a CV Tailor resume, which is deliberately never saved as one.
   * It has to belong to the caller in lynq-file-storage, and it is checked
   * there before the application is registered.
   */
  private String fileId;

  /**
   * How to call the document of a fileId application in the candidate's list of
   * applications. Ignored when resumeId is given, because the resume names
   * itself.
   */
  private String resumeName;

}
