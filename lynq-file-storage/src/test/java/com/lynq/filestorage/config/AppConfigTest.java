package com.lynq.filestorage.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.net.URI;
import java.time.Duration;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

class AppConfigTest {

  private static final String CLIENT_ENDPOINT = "http://localstack:4566";
  private static final String PUBLIC_ENDPOINT = "http://localhost:4566";

  private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
      .withUserConfiguration(AppConfig.class)
      .withPropertyValues(
          "lynq.aws.region=us-east-1",
          "lynq.aws.access-key-id=test",
          "lynq.aws.secret-access-key=test");

  private String presignedHost(S3Presigner presigner) {
    return presigner.presignPutObject(PutObjectPresignRequest.builder()
        .signatureDuration(Duration.ofMinutes(15))
        .putObjectRequest(PutObjectRequest.builder()
            .bucket("lynq-bucket")
            .key("lynq/any-file.jpeg")
            .build())
        .build())
        .url()
        .getAuthority();
  }

  private String clientHost(S3Client client) {
    return client.serviceClientConfiguration().endpointOverride()
        .map(URI::getAuthority)
        .orElse("");
  }

  @Test
  void presignsAgainstThePublicEndpointWhileTheClientKeepsTheInternalOne() {
    contextRunner
        .withPropertyValues(
            "lynq.aws.endpoint=" + CLIENT_ENDPOINT,
            "lynq.aws.public-endpoint=" + PUBLIC_ENDPOINT)
        .run(context -> {
          assertThat(clientHost(context.getBean(S3Client.class)), is("localstack:4566"));
          assertThat(presignedHost(context.getBean(S3Presigner.class)), is("localhost:4566"));
        });
  }

  @Test
  void fallsBackToTheClientEndpointWhenNoPublicEndpointIsSet() {
    contextRunner
        .withPropertyValues("lynq.aws.endpoint=" + CLIENT_ENDPOINT)
        .run(context -> {
          assertThat(clientHost(context.getBean(S3Client.class)), is("localstack:4566"));
          assertThat(presignedHost(context.getBean(S3Presigner.class)), is("localstack:4566"));
        });
  }

  @Test
  void followsTheClientEndpointWhenThePublicOneIsDeclaredButBlank() {
    contextRunner
        .withPropertyValues(
            "lynq.aws.endpoint=" + CLIENT_ENDPOINT,
            "lynq.aws.public-endpoint=")
        .run(context ->
            assertThat(presignedHost(context.getBean(S3Presigner.class)), is("localstack:4566")));
  }

  @Test
  void followsAnEndpointRegisteredAtRuntimeWhenThePublicOneIsBlank() {
    contextRunner
        .withPropertyValues(
            "lynq.aws.endpoint=http://127.0.0.1:55001",
            "lynq.aws.public-endpoint=")
        .run(context ->
            assertThat(presignedHost(context.getBean(S3Presigner.class)), is("127.0.0.1:55001")));
  }

  @Test
  void presignsAgainstAmazonWhenBothEndpointsAreEmpty() {
    contextRunner.run(context -> {
      assertThat(clientHost(context.getBean(S3Client.class)), is(""));
      assertThat(presignedHost(context.getBean(S3Presigner.class)),
          is("lynq-bucket.s3.amazonaws.com"));
    });
  }

}
