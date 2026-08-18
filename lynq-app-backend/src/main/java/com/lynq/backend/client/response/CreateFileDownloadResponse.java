package com.lynq.backend.client.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A short-lived pre-signed URL issued by lynq-file-storage for reading a single stored file.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateFileDownloadResponse {

  private String fileId;
  private String s3Key;
  private String downloadUrl;

}
