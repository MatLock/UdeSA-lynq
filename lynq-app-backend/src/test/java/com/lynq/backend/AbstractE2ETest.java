package com.lynq.backend;

import org.mockserver.client.MockServerClient;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MockServerContainer;
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

  private static final DockerImageName MOCKSERVER_IMAGE =
      DockerImageName.parse("mockserver/mockserver:5.15.0");

  private static final DockerImageName LOCALSTACK_IMAGE =
      DockerImageName.parse("localstack/localstack:3.8.1");

  protected static final String AWS_BUCKET = "lynq-test-bucket";

  /**
   * Stands in for the lynq-iam identity provider. Tests register expectations on
   * its {@code /auth/validate} and {@code /auth/userinfo} endpoints through
   * {@link #lynqIamMock}.
   */
  protected static final MockServerContainer LYNQ_IAM = new MockServerContainer(MOCKSERVER_IMAGE)
      .withReuse(true);

  /**
   * Stands in for the lynq-ml service. Tests register expectations on its
   * {@code /skill-enhance} endpoint through {@link #lynqMlMock}.
   */
  protected static final MockServerContainer LYNQ_ML = new MockServerContainer(MOCKSERVER_IMAGE)
      .withReuse(true);

  /**
   * Stands in for AWS S3 so the profile-image upload/download flow can be exercised
   * end to end against a real S3 API. The application's S3 beans are pointed at this
   * container through {@code lynq.aws.endpoint}.
   */
  protected static final LocalStackContainer LOCALSTACK = new LocalStackContainer(LOCALSTACK_IMAGE)
      .withServices(LocalStackContainer.Service.S3)
      .withReuse(true);

  protected static MockServerClient lynqIamMock;
  protected static MockServerClient lynqMlMock;
  protected static S3Client s3TestClient;

  static {
    LYNQ_IAM.start();
    lynqIamMock = new MockServerClient(LYNQ_IAM.getHost(), LYNQ_IAM.getServerPort());

    LYNQ_ML.start();
    lynqMlMock = new MockServerClient(LYNQ_ML.getHost(), LYNQ_ML.getServerPort());

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
    registry.add("lynq.iam.url", LYNQ_IAM::getEndpoint);
    registry.add("lynq.ml.url", LYNQ_ML::getEndpoint);
    registry.add("lynq.aws.endpoint", () -> LOCALSTACK.getEndpoint().toString());
    registry.add("lynq.aws.region", LOCALSTACK::getRegion);
    registry.add("lynq.aws.access-key-id", LOCALSTACK::getAccessKey);
    registry.add("lynq.aws.secret-access-key", LOCALSTACK::getSecretKey);
    registry.add("lynq.aws.bucket-name", () -> AWS_BUCKET);
  }
}