package com.lynq.backend.controller.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * What is left of a resume once it is deleted. Carries the id of the PDF that backed it so the
 * caller can drop the file too: this service stores the resume row, lynq-file-storage stores the
 * PDF, and only lynq-bff talks to both — so the file id has to travel back rather than be cleaned
 * up here.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeleteResumeRestResponse {

  private String id;
  /** The lynq-file-storage id of the deleted resume's PDF; null when it had none. */
  private String fileId;

}
