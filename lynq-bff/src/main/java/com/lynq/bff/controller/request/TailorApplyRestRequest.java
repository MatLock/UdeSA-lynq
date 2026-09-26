package com.lynq.bff.controller.request;

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
public class TailorApplyRestRequest {

  /**
   * How to render the tailored resume into the PDF the application is made
   * with. MODERN when it is left out.
   */
  private ResumeTemplate template;

  /**
   * How to call that PDF in the candidate's list of applications. The title of
   * the posting when it is left out.
   */
  private String resumeName;

  /**
   * A PDF of this conversation that was already rendered, to apply with instead
   * of rendering a second one. An external posting needs the document in the
   * candidate's hands before they leave, so the browser renders it, downloads
   * it and hands the file back here. lynq-app-backend checks it belongs to the
   * caller before it registers the application.
   */
  private String fileId;
}
