package com.lynq.backend.client.response;

import java.time.LocalDateTime;
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
public class StoredFileResponse {

  private String fileId;
  private String fileName;
  private String contentType;
  private String s3Key;
  private String status;
  private LocalDateTime createdOn;
  private LocalDateTime updatedOn;

}
