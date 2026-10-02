package com.lynq.backend.config;

import java.net.URI;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.SnsClientBuilder;

@Configuration
@EnableConfigurationProperties({AwsProperties.class, DomainEventsProperties.class})
public class DomainEventsConfig {

  @Bean(destroyMethod = "close")
  public SnsClient snsClient(AwsProperties aws) {
    SnsClientBuilder builder = SnsClient.builder()
        .region(Region.of(aws.region()))
        .credentialsProvider(credentialsOf(aws));
    if (!aws.endpoint().isBlank()) {
      builder.endpointOverride(URI.create(aws.endpoint()));
    }
    return builder.build();
  }

  private static AwsCredentialsProvider credentialsOf(AwsProperties aws) {
    if (aws.accessKeyId().isBlank() || aws.secretAccessKey().isBlank()) {
      return DefaultCredentialsProvider.builder().build();
    }
    return StaticCredentialsProvider.create(
        AwsBasicCredentials.create(aws.accessKeyId(), aws.secretAccessKey()));
  }
}
