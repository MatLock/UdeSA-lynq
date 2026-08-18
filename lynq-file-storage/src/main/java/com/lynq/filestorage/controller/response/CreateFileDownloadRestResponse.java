package com.lynq.filestorage.controller.response;

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
public class CreateFileDownloadRestResponse {

  private String fileId;
  private String s3Key;
  private String downloadUrl;

}
