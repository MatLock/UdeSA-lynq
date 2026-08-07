package com.lynq.backend.client.response;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * File metadata as lynq-file-storage holds it. {@code status} is its lifecycle state
 * ({@code PENDING} until the upload is confirmed, then {@code AVAILABLE}) and is carried as a
 * string so this service does not have to mirror the enum.
 */
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
