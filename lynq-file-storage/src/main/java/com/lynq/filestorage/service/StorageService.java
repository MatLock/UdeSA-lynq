package com.lynq.filestorage.service;

import com.lynq.filestorage.aspect.AuditLog;
import java.time.Duration;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

@Service
public class StorageService {

  private static final String OBJECT_KEY_FORMAT = "lynq/%s/%s";
  private static final Duration PRE_SIGNED_URL_EXPIRATION = Duration.ofMinutes(15);

  private final S3Presigner s3Presigner;
  private final S3Client s3Client;
  private final String bucketName;

  public StorageService(S3Presigner s3Presigner, S3Client s3Client,
      @Value("${lynq.aws.bucket-name}") String bucketName) {
    this.s3Presigner = s3Presigner;
    this.s3Client = s3Client;
    this.bucketName = bucketName;
  }

  public String buildObjectKey(String fileId, String fileName) {
    return String.format(OBJECT_KEY_FORMAT, fileId, fileName);
  }

  @AuditLog
  public PreSignedUploadUrl createUploadPreSignedUrl(String s3Key, String contentType) {
    PutObjectRequest.Builder putObjectRequest = PutObjectRequest.builder()
        .bucket(bucketName)
        .key(s3Key);
    if (contentType != null && !contentType.isBlank()) {
      putObjectRequest.contentType(contentType);
    }
    PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
        .signatureDuration(PRE_SIGNED_URL_EXPIRATION)
        .putObjectRequest(putObjectRequest.build())
        .build();
    PresignedPutObjectRequest presignedRequest = s3Presigner.presignPutObject(presignRequest);

    return new PreSignedUploadUrl(s3Key, presignedRequest.url().toString());
  }

  @AuditLog
  public String createDownloadPreSignedUrl(String s3Key) {
    GetObjectRequest getObjectRequest = GetObjectRequest.builder()
        .bucket(bucketName)
        .key(s3Key)
        .build();
    GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
        .signatureDuration(PRE_SIGNED_URL_EXPIRATION)
        .getObjectRequest(getObjectRequest)
        .build();
    PresignedGetObjectRequest presignedRequest = s3Presigner.presignGetObject(presignRequest);

    return presignedRequest.url().toString();
  }

  @AuditLog
  public void deleteObject(String s3Key) {
    DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
        .bucket(bucketName)
        .key(s3Key)
        .build();

    s3Client.deleteObject(deleteObjectRequest);
  }

  @AuditLog
  public Optional<HeadObjectResponse> findObjectMetadata(String s3Key) {
    HeadObjectRequest headObjectRequest = HeadObjectRequest.builder()
        .bucket(bucketName)
        .key(s3Key)
        .build();
    try {
      return Optional.of(s3Client.headObject(headObjectRequest));
    } catch (NoSuchKeyException e) {
      return Optional.empty();
    }
  }

}
