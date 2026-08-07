package com.lynq.backend.client.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A file registered as PENDING in lynq-file-storage together with the pre-signed URL the browser
 * uploads the bytes to. {@code fileId} is the only part this service persists.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateFileUploadResponse {

  private String fileId;
  private String s3Key;
  private String uploadUrl;

}
