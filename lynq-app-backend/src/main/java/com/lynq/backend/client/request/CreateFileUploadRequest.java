package com.lynq.backend.client.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Registers a file in lynq-file-storage so it can hand back a pre-signed upload URL. The content
 * type is optional: lynq-file-storage reads the real one off the object when the upload is
 * confirmed.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateFileUploadRequest {

  private String fileName;
  private String contentType;

}
