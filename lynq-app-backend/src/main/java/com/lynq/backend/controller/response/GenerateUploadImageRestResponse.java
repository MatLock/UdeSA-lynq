package com.lynq.backend.controller.response;

import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class GenerateUploadImageRestResponse {

  private String preSignedUrl;
  private String fileId;

}
