package com.lynq.bff.client.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.lynq.bff.enums.StoredFileStatus;
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
@JsonIgnoreProperties(ignoreUnknown = true)
public class FileResponse {

  private String fileId;
  private String fileName;
  private String contentType;
  private String s3Key;
  private StoredFileStatus status;
  private LocalDateTime createdOn;
  private LocalDateTime updatedOn;
}
