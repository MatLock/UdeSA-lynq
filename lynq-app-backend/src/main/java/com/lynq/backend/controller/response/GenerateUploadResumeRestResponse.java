package com.lynq.backend.controller.response;

import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class GenerateUploadResumeRestResponse {

  private String preSignedUrl;

}