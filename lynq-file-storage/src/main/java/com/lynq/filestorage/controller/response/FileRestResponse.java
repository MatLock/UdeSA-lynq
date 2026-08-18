package com.lynq.filestorage.controller.response;

import com.lynq.filestorage.enums.StoredFileStatus;
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
public class FileRestResponse {

  private String fileId;
  private String fileName;
  private String contentType;
  private String s3Key;
  private StoredFileStatus status;
  private LocalDateTime createdOn;
  private LocalDateTime updatedOn;

}
