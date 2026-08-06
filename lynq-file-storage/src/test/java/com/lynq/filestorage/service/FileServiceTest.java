package com.lynq.filestorage.service;

import com.lynq.filestorage.controller.request.CreateFileUploadRequest;
import com.lynq.filestorage.enums.StoredFileStatus;
import com.lynq.filestorage.exceptions.BadRequestException;
import com.lynq.filestorage.exceptions.NotFoundException;
import com.lynq.filestorage.model.StoredFileEntity;
import com.lynq.filestorage.repository.StoredFileRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileServiceTest {

  private static final String FILE_NAME = "cv.pdf";
  private static final String CONTENT_TYPE = "application/pdf";
  private static final String FILE_ID = "0195f2c1-3b1a-7c2d-9f31-3f6a5f2c9d41";
  private static final String UNKNOWN_FILE_ID = "00000000-0000-0000-0000-000000000000";
  private static final String S3_KEY = "lynq/" + FILE_ID + "/" + FILE_NAME;
  private static final String UPLOAD_URL = "https://s3.local/upload?signature=abc";
  private static final String DOWNLOAD_URL = "https://s3.local/download?signature=abc";
  private static final String DETECTED_CONTENT_TYPE = "application/octet-stream";
  private static final long OBJECT_SIZE = 4096L;

  @Mock
  private StoredFileRepository storedFileRepository;

  @Mock
  private StorageService storageService;

  private FileService fileService;

  @BeforeEach
  void setUp() {
    fileService = new FileService(storedFileRepository, storageService);
  }

  @Test
  void createUploadPersistsThePendingMetadataWithAGeneratedIdAndObjectKey() {
    when(storageService.buildObjectKey(anyString(), anyString())).thenReturn(S3_KEY);
    when(storedFileRepository.save(any(StoredFileEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    ArgumentCaptor<StoredFileEntity> captor = ArgumentCaptor.forClass(StoredFileEntity.class);

    StoredFileEntity storedFile = fileService.createUpload(buildRequest());

    verify(storedFileRepository).save(captor.capture());
    StoredFileEntity saved = captor.getValue();
    assertThat(saved.getId(), is(notNullValue()));
    assertThat(saved.getFileName(), is(FILE_NAME));
    assertThat(saved.getContentType(), is(CONTENT_TYPE));
    assertThat(saved.getS3Key(), is(S3_KEY));
    assertThat(saved.getStatus(), is(StoredFileStatus.PENDING));
    assertThat(storedFile.getId(), is(saved.getId()));
  }

  @Test
  void createUploadUrlDelegatesToTheStorageServiceWithTheStoredKeyAndContentType() {
    StoredFileEntity storedFile = buildStoredFile(StoredFileStatus.PENDING);
    when(storageService.createUploadPreSignedUrl(S3_KEY, CONTENT_TYPE))
        .thenReturn(new PreSignedUploadUrl(S3_KEY, UPLOAD_URL));

    PreSignedUploadUrl preSignedUploadUrl = fileService.createUploadUrl(storedFile);

    assertThat(preSignedUploadUrl.url(), is(UPLOAD_URL));
    assertThat(preSignedUploadUrl.s3Path(), is(S3_KEY));
  }

  @Test
  void confirmUploadMarksTheFileAvailableAndCopiesTheContentTypeReportedByS3() {
    StoredFileEntity storedFile = buildStoredFile(StoredFileStatus.PENDING);
    when(storedFileRepository.findById(FILE_ID)).thenReturn(Optional.of(storedFile));
    when(storageService.findObjectMetadata(S3_KEY)).thenReturn(Optional.of(
        HeadObjectResponse.builder().contentLength(OBJECT_SIZE).contentType(DETECTED_CONTENT_TYPE).build()));
    when(storedFileRepository.save(any(StoredFileEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    StoredFileEntity confirmed = fileService.confirmUpload(FILE_ID);

    assertThat(confirmed.getStatus(), is(StoredFileStatus.AVAILABLE));
    assertThat(confirmed.getContentType(), is(DETECTED_CONTENT_TYPE));
  }

  @Test
  void confirmUploadFailsWithBadRequestWhenTheObjectIsNotInTheBucketYet() {
    StoredFileEntity storedFile = buildStoredFile(StoredFileStatus.PENDING);
    when(storedFileRepository.findById(FILE_ID)).thenReturn(Optional.of(storedFile));
    when(storageService.findObjectMetadata(S3_KEY)).thenReturn(Optional.empty());

    assertThrows(BadRequestException.class, () -> fileService.confirmUpload(FILE_ID));
    verify(storedFileRepository, never()).save(any(StoredFileEntity.class));
  }

  @Test
  void findFileFailsWithNotFoundWhenTheFileDoesNotExist() {
    when(storedFileRepository.findById(UNKNOWN_FILE_ID)).thenReturn(Optional.empty());

    assertThrows(NotFoundException.class, () -> fileService.findFile(UNKNOWN_FILE_ID));
  }

  @Test
  void createDownloadUrlsSignsEveryKnownFileAndOmitsTheUnknownOnes() {
    StoredFileEntity storedFile = buildStoredFile(StoredFileStatus.AVAILABLE);
    when(storedFileRepository.findAllById(Set.of(FILE_ID, UNKNOWN_FILE_ID)))
        .thenReturn(List.of(storedFile));
    when(storageService.createDownloadPreSignedUrl(S3_KEY)).thenReturn(DOWNLOAD_URL);

    Map<String, String> downloadUrls =
        fileService.createDownloadUrls(List.of(FILE_ID, UNKNOWN_FILE_ID));

    assertThat(downloadUrls.size(), is(1));
    assertThat(downloadUrls.get(FILE_ID), is(DOWNLOAD_URL));
  }

  @Test
  void createDownloadUrlsDeduplicatesRepeatedIdsBeforeHittingTheRepository() {
    StoredFileEntity storedFile = buildStoredFile(StoredFileStatus.AVAILABLE);
    when(storedFileRepository.findAllById(Set.of(FILE_ID))).thenReturn(List.of(storedFile));
    when(storageService.createDownloadPreSignedUrl(S3_KEY)).thenReturn(DOWNLOAD_URL);

    Map<String, String> downloadUrls =
        fileService.createDownloadUrls(List.of(FILE_ID, FILE_ID, FILE_ID));

    assertThat(downloadUrls.size(), is(1));
    assertThat(downloadUrls.get(FILE_ID), is(DOWNLOAD_URL));
  }

  @Test
  void createDownloadUrlDelegatesToTheStorageServiceWithTheStoredKey() {
    when(storageService.createDownloadPreSignedUrl(S3_KEY)).thenReturn(DOWNLOAD_URL);

    String downloadUrl = fileService.createDownloadUrl(buildStoredFile(StoredFileStatus.AVAILABLE));

    assertThat(downloadUrl, is(DOWNLOAD_URL));
  }

  private CreateFileUploadRequest buildRequest() {
    return CreateFileUploadRequest.builder()
        .fileName(FILE_NAME)
        .contentType(CONTENT_TYPE)
        .build();
  }

  private StoredFileEntity buildStoredFile(StoredFileStatus status) {
    LocalDateTime now = LocalDateTime.now();
    return StoredFileEntity.builder()
        .id(FILE_ID)
        .fileName(FILE_NAME)
        .contentType(CONTENT_TYPE)
        .s3Key(S3_KEY)
        .status(status)
        .createdOn(now)
        .updatedOn(now)
        .build();
  }
}
