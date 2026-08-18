package com.lynq.filestorage.controller.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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
public class CreateFileUploadRequest {

  @NotBlank
  @Size(max = 255)
  private String fileName;

  @Size(max = 255)
  private String contentType;

}
