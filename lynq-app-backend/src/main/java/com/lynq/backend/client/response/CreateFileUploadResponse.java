package com.lynq.backend.client.response;

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
public class CreateFileUploadResponse {

  private String fileId;
  private String s3Key;
  private String uploadUrl;

}
