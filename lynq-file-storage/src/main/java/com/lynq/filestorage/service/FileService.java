package com.lynq.filestorage.service;

import com.fasterxml.uuid.Generators;
import com.lynq.filestorage.aspect.AuditLog;
import com.lynq.filestorage.controller.request.CreateFileUploadRequest;
import com.lynq.filestorage.enums.StoredFileStatus;
import com.lynq.filestorage.exceptions.BadRequestException;
import com.lynq.filestorage.exceptions.ForbiddenException;
import com.lynq.filestorage.exceptions.NotFoundException;
import com.lynq.filestorage.model.StoredFileEntity;
import com.lynq.filestorage.repository.StoredFileRepository;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FileService {

  private static final String FILE_NOT_FOUND_MSG = "File '%s' not found";
  private static final String UPLOAD_NOT_COMPLETED_MSG =
      "File '%s' has no object in the bucket yet; complete the upload before confirming it";
  private static final String NOT_THE_OWNER_MSG = "File '%s' belongs to another user";

  private final StoredFileRepository storedFileRepository;
  private final StorageService storageService;

  public FileService(StoredFileRepository storedFileRepository, StorageService storageService) {
    this.storedFileRepository = storedFileRepository;
    this.storageService = storageService;
  }

  @AuditLog
  @Transactional
  public StoredFileEntity createUpload(CreateFileUploadRequest request, String ownerUserId) {
    String fileId = Generators.timeBasedEpochGenerator().generate().toString();
    String s3Key = storageService.buildObjectKey(fileId, request.getFileName());
    LocalDateTime now = LocalDateTime.now();

    StoredFileEntity storedFile = StoredFileEntity.builder()
        .id(fileId)
        .fileName(request.getFileName())
        .contentType(request.getContentType())
        .s3Key(s3Key)
        .ownerUserId(ownerUserId)
        .status(StoredFileStatus.PENDING)
        .createdOn(now)
        .updatedOn(now)
        .build();

    return storedFileRepository.save(storedFile);
  }

  @AuditLog
  public PreSignedUploadUrl createUploadUrl(StoredFileEntity storedFile) {
    return storageService.createUploadPreSignedUrl(storedFile.getS3Key(), storedFile.getContentType());
  }

  @AuditLog
  @Transactional
  public StoredFileEntity confirmUpload(String fileId, String callerUserId) {
    StoredFileEntity storedFile = findFile(fileId);
    requireOwner(storedFile, callerUserId);
    HeadObjectResponse metadata = storageService.findObjectMetadata(storedFile.getS3Key())
        .orElseThrow(() -> new BadRequestException(String.format(UPLOAD_NOT_COMPLETED_MSG, fileId)));

    if (metadata.contentType() != null) {
      storedFile.setContentType(metadata.contentType());
    }
    storedFile.setStatus(StoredFileStatus.AVAILABLE);
    storedFile.setUpdatedOn(LocalDateTime.now());

    return storedFileRepository.save(storedFile);
  }

  @AuditLog
  @Transactional(readOnly = true)
  public StoredFileEntity findFile(String fileId) {
    return storedFileRepository.findById(fileId)
        .orElseThrow(() -> new NotFoundException(String.format(FILE_NOT_FOUND_MSG, fileId)));
  }

  @AuditLog
  @Transactional
  public void deleteFile(String fileId, String callerUserId) {
    storedFileRepository.findById(fileId).ifPresent(storedFile -> {
      requireOwner(storedFile, callerUserId);
      storageService.deleteObject(storedFile.getS3Key());
      storedFileRepository.delete(storedFile);
    });
  }

  private void requireOwner(StoredFileEntity storedFile, String callerUserId) {
    String owner = storedFile.getOwnerUserId();
    if (owner != null && !owner.equals(callerUserId)) {
      throw new ForbiddenException(String.format(NOT_THE_OWNER_MSG, storedFile.getId()));
    }
  }

  @AuditLog
  public String createDownloadUrl(StoredFileEntity storedFile) {
    return storageService.createDownloadPreSignedUrl(storedFile.getS3Key());
  }

  @AuditLog
  @Transactional(readOnly = true)
  public Map<String, String> createDownloadUrls(Collection<String> fileIds) {
    return storedFileRepository.findAllById(Set.copyOf(fileIds)).stream()
        .collect(Collectors.toMap(
            StoredFileEntity::getId,
            storedFile -> storageService.createDownloadPreSignedUrl(storedFile.getS3Key())));
  }

}
