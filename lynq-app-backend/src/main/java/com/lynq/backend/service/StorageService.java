package com.lynq.backend.service;

import com.lynq.backend.aspect.AuditLog;
import com.lynq.backend.model.CompanyEntity;
import com.lynq.backend.model.UserEntity;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

@Service
public class StorageService {

  private static final String USER_PROFILE_PATH_FORMAT = "lynq/users/%s/profile/%s";
  private static final String COMPANY_PROFILE_PATH_FORMAT = "lynq/companies/%s/profile/%s";
  private static final Duration PRE_SIGNED_URL_EXPIRATION = Duration.ofMinutes(15);

  private final S3Presigner s3Presigner;
  private final S3Client s3Client;
  private final String bucketName;

  public StorageService(S3Presigner s3Presigner, S3Client s3Client, @Value("${lynq.aws.bucket-name}") String bucketName) {
    this.s3Presigner = s3Presigner;
    this.bucketName = bucketName;
    this.s3Client = s3Client;
  }

  @AuditLog
  public PreSignedUploadUrl createUserProfilePreSignedUrl(UserEntity userEntity, String fileName) {
    return createProfilePreSignedUrl(
        String.format(USER_PROFILE_PATH_FORMAT, userEntity.getId(), fileName));
  }

  @AuditLog
  public PreSignedUploadUrl createCompanyProfilePreSignedUrl(CompanyEntity companyEntity, String fileName) {
    return createProfilePreSignedUrl(
        String.format(COMPANY_PROFILE_PATH_FORMAT, companyEntity.getId(), fileName));
  }

  private PreSignedUploadUrl createProfilePreSignedUrl(String s3Path) {
    PutObjectRequest putObjectRequest = PutObjectRequest.builder()
        .bucket(bucketName)
        .key(s3Path)
        .build();
    PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
        .signatureDuration(PRE_SIGNED_URL_EXPIRATION)
        .putObjectRequest(putObjectRequest)
        .build();
    PresignedPutObjectRequest presignedRequest = s3Presigner.presignPutObject(presignRequest);

    return new PreSignedUploadUrl(s3Path, presignedRequest.url().toString());
  }

  @AuditLog
  public String obtainUserProfilePreSignedUrl(UserEntity userEntity) {
    return obtainProfilePreSignedUrl(userEntity.getProfileImageUrl());
  }

  @AuditLog
  public String obtainProfilePreSignedUrl(String s3Path) {

    GetObjectRequest getObjectRequest = GetObjectRequest.builder()
        .bucket(bucketName)
        .key(s3Path)
        .build();
    GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
        .signatureDuration(PRE_SIGNED_URL_EXPIRATION)
        .getObjectRequest(getObjectRequest)
        .build();
    PresignedGetObjectRequest presignedRequest = s3Presigner.presignGetObject(presignRequest);

    return presignedRequest.url().toString();
  }

  @AuditLog
  public void deleteObject(String s3Path) {
    DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
        .bucket(bucketName)
        .key(s3Path)
        .build();

    s3Client.deleteObject(deleteObjectRequest);
  }

}