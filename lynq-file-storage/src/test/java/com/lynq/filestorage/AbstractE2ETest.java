package com.lynq.filestorage;

import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("test")
@Testcontainers
public abstract class AbstractE2ETest {

  private static final DockerImageName LOCALSTACK_IMAGE =
      DockerImageName.parse("localstack/localstack:3.8.1");

  protected static final String AWS_BUCKET = "lynq-test-bucket";

  protected static final LocalStackContainer LOCALSTACK = new LocalStackContainer(LOCALSTACK_IMAGE)
      .withServices(LocalStackContainer.Service.S3)
      .withReuse(true);

  protected static S3Client s3TestClient;

  static {
    LOCALSTACK.start();
    s3TestClient = S3Client.builder()
        .endpointOverride(LOCALSTACK.getEndpoint())
        .credentialsProvider(StaticCredentialsProvider.create(
            AwsBasicCredentials.create(LOCALSTACK.getAccessKey(), LOCALSTACK.getSecretKey())))
        .region(Region.of(LOCALSTACK.getRegion()))
        .forcePathStyle(true)
        .build();
    s3TestClient.createBucket(CreateBucketRequest.builder().bucket(AWS_BUCKET).build());
  }

  @DynamicPropertySource
  static void registerDynamicProperties(DynamicPropertyRegistry registry) {
    registry.add("lynq.aws.endpoint", () -> LOCALSTACK.getEndpoint().toString());
    registry.add("lynq.aws.region", LOCALSTACK::getRegion);
    registry.add("lynq.aws.access-key-id", LOCALSTACK::getAccessKey);
    registry.add("lynq.aws.secret-access-key", LOCALSTACK::getSecretKey);
    registry.add("lynq.aws.bucket-name", () -> AWS_BUCKET);
  }
}
