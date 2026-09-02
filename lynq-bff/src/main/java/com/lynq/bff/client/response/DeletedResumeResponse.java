package com.lynq.bff.client.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * What lynq-app-backend answers when a resume is deleted: the row is gone there, and {@code fileId}
 * points at the PDF that backed it, which still lives in lynq-file-storage. Only this service talks
 * to both, so dropping that file is this side's job.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class DeletedResumeResponse {

  private String id;

  private String fileId;

}
