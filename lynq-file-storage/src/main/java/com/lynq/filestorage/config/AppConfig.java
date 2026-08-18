package com.lynq.filestorage.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
public class AppConfig {

  @Bean
  public ObjectMapper createObjectMapper() {
    ObjectMapper objectMapper = new ObjectMapper();
    objectMapper.registerModule(new JavaTimeModule());
    return objectMapper;
  }

  @Bean
  public S3Client s3Client(
      @Value("${lynq.aws.region}") String region,
      @Value("${lynq.aws.access-key-id}") String accessKeyId,
      @Value("${lynq.aws.secret-access-key}") String secretAccessKey,
      @Value("${lynq.aws.endpoint:}") String endpoint) {
    S3ClientBuilder builder = S3Client.builder()
        .region(Region.of(region))
        .credentialsProvider(StaticCredentialsProvider.create(
            AwsBasicCredentials.create(accessKeyId, secretAccessKey)));
    if (!endpoint.isBlank()) {
      builder.endpointOverride(URI.create(endpoint)).forcePathStyle(true);
    }
    return builder.build();
  }

  @Bean
  public S3Presigner s3Presigner(
      @Value("${lynq.aws.region}") String region,
      @Value("${lynq.aws.access-key-id}") String accessKeyId,
      @Value("${lynq.aws.secret-access-key}") String secretAccessKey,
      @Value("${lynq.aws.endpoint:}") String endpoint) {
    S3Presigner.Builder builder = S3Presigner.builder()
        .region(Region.of(region))
        .credentialsProvider(StaticCredentialsProvider.create(
            AwsBasicCredentials.create(accessKeyId, secretAccessKey)));
    if (!endpoint.isBlank()) {
      builder.endpointOverride(URI.create(endpoint))
          .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build());
    }
    return builder.build();
  }

}
