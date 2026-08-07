package com.lynq.filestorage.model;

import com.lynq.filestorage.enums.StoredFileStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "stored_files")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StoredFileEntity {

  @Id
  @Column(name = "id", length = 36, nullable = false)
  private String id;

  @Column(name = "file_name", length = 255, nullable = false)
  private String fileName;

  @Column(name = "content_type", length = 255)
  private String contentType;

  @Column(name = "s3_key", length = 1024, nullable = false)
  private String s3Key;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", length = 20, nullable = false)
  private StoredFileStatus status;

  @Column(name = "created_on", nullable = false)
  private LocalDateTime createdOn;

  @Column(name = "updated_on", nullable = false)
  private LocalDateTime updatedOn;

}
