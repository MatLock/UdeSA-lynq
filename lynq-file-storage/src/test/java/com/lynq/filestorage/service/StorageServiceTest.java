package com.lynq.filestorage.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.net.URL;
import java.net.URI;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StorageServiceTest {

  private static final String BUCKET_NAME = "lynq-test-bucket";
  private static final String FILE_ID = "0195f2c1-3b1a-7c2d-9f31-3f6a5f2c9d41";
  private static final String FILE_NAME = "cv.pdf";
  private static final String S3_KEY = "lynq/" + FILE_ID + "/" + FILE_NAME;
  private static final String CONTENT_TYPE = "application/pdf";
  private static final String PRE_SIGNED_URL = "https://s3.local/lynq-test-bucket/object?signature=abc";
  private static final long OBJECT_SIZE = 2048L;

  @Mock
  private S3Presigner s3Presigner;

  @Mock
  private S3Client s3Client;

  @Mock
  private PresignedPutObjectRequest presignedPutObjectRequest;

  @Mock
  private PresignedGetObjectRequest presignedGetObjectRequest;

  private StorageService storageService;

  @BeforeEach
  void setUp() {
    storageService = new StorageService(s3Presigner, s3Client, BUCKET_NAME);
  }

  @Test
  void buildObjectKeyPrefixesTheObjectWithTheFileId() {
    String objectKey = storageService.buildObjectKey(FILE_ID, FILE_NAME);

    assertThat(objectKey, is(S3_KEY));
  }

  @Test
  void createUploadPreSignedUrlReturnsTheKeyAndTheSignedPutUrl() throws Exception {
    when(presignedPutObjectRequest.url()).thenReturn(url());
    when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class)))
        .thenReturn(presignedPutObjectRequest);

    PreSignedUploadUrl preSignedUploadUrl = storageService.createUploadPreSignedUrl(S3_KEY, CONTENT_TYPE);

    assertThat(preSignedUploadUrl.s3Path(), is(S3_KEY));
    assertThat(preSignedUploadUrl.url(), is(PRE_SIGNED_URL));
  }

  @Test
  void createUploadPreSignedUrlSignsAgainstTheConfiguredBucketAndKey() throws Exception {
    when(presignedPutObjectRequest.url()).thenReturn(url());
    when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class)))
        .thenReturn(presignedPutObjectRequest);
    ArgumentCaptor<PutObjectPresignRequest> captor = ArgumentCaptor.forClass(PutObjectPresignRequest.class);

    storageService.createUploadPreSignedUrl(S3_KEY, CONTENT_TYPE);

    verify(s3Presigner).presignPutObject(captor.capture());
    assertThat(captor.getValue().putObjectRequest().bucket(), is(BUCKET_NAME));
    assertThat(captor.getValue().putObjectRequest().key(), is(S3_KEY));
    assertThat(captor.getValue().putObjectRequest().contentType(), is(CONTENT_TYPE));
  }

  @Test
  void createUploadPreSignedUrlOmitsContentTypeWhenTheCallerDidNotProvideOne() throws Exception {
    when(presignedPutObjectRequest.url()).thenReturn(url());
    when(s3Presigner.presignPutObject(any(PutObjectPresignRequest.class)))
        .thenReturn(presignedPutObjectRequest);
    ArgumentCaptor<PutObjectPresignRequest> captor = ArgumentCaptor.forClass(PutObjectPresignRequest.class);

    storageService.createUploadPreSignedUrl(S3_KEY, "  ");

    verify(s3Presigner).presignPutObject(captor.capture());
    assertThat(captor.getValue().putObjectRequest().contentType(), is((String) null));
  }

  @Test
  void createDownloadPreSignedUrlReturnsTheSignedGetUrl() throws Exception {
    when(presignedGetObjectRequest.url()).thenReturn(url());
    when(s3Presigner.presignGetObject(any(GetObjectPresignRequest.class)))
        .thenReturn(presignedGetObjectRequest);

    String downloadUrl = storageService.createDownloadPreSignedUrl(S3_KEY);

    assertThat(downloadUrl, is(PRE_SIGNED_URL));
  }

  @Test
  void deleteObjectRemovesTheKeyFromTheConfiguredBucket() {
    ArgumentCaptor<DeleteObjectRequest> captor = ArgumentCaptor.forClass(DeleteObjectRequest.class);

    storageService.deleteObject(S3_KEY);

    verify(s3Client).deleteObject(captor.capture());
    assertThat(captor.getValue().bucket(), is(BUCKET_NAME));
    assertThat(captor.getValue().key(), is(S3_KEY));
  }

  @Test
  void findObjectMetadataReturnsTheHeadResponseWhenTheObjectExists() {
    HeadObjectResponse headObjectResponse = HeadObjectResponse.builder()
        .contentLength(OBJECT_SIZE)
        .contentType(CONTENT_TYPE)
        .build();
    when(s3Client.headObject(any(HeadObjectRequest.class))).thenReturn(headObjectResponse);

    Optional<HeadObjectResponse> metadata = storageService.findObjectMetadata(S3_KEY);

    assertThat(metadata.isPresent(), is(true));
    assertThat(metadata.get().contentLength(), is(OBJECT_SIZE));
  }

  @Test
  void findObjectMetadataReturnsEmptyWhenTheObjectWasNeverUploaded() {
    when(s3Client.headObject(any(HeadObjectRequest.class)))
        .thenThrow(NoSuchKeyException.builder().message("not found").build());

    Optional<HeadObjectResponse> metadata = storageService.findObjectMetadata(S3_KEY);

    assertThat(metadata.isEmpty(), is(true));
  }

  private static URL url() throws Exception {
    return URI.create(PRE_SIGNED_URL).toURL();
  }
}
