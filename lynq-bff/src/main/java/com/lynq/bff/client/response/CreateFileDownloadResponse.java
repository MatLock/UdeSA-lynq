package com.lynq.bff.client.response;

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
public class CreateFileDownloadResponse {

  private String fileId;
  private String s3Key;
  private String downloadUrl;
}
