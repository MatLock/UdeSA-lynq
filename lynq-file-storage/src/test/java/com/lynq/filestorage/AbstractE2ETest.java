package com.lynq.filestorage;

import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

import org.mockserver.client.MockServerClient;
import org.mockserver.model.MediaType;
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

  private static final DockerImageName LOCALSTACK_IMAGE =
      DockerImageName.parse("localstack/localstack:3.8.1");

  private static final DockerImageName MOCKSERVER_IMAGE =
      DockerImageName.parse("mockserver/mockserver:5.15.0");

  private static final String USERINFO_PATH = "/auth/user-info";
  private static final String AUTHORIZATION_HEADER = "Authorization";

  protected static final String AWS_BUCKET = "lynq-test-bucket";

  protected static final String USER_ID = "11111111-1111-1111-1111-111111111111";
  protected static final String OTHER_USER_ID = "99999999-9999-9999-9999-999999999999";

  /** One bearer per identity: lynq-iam is what turns a token into a user id. */
  protected static final String BEARER_TOKEN = "Bearer access-token-of-the-owner";
  protected static final String OTHER_BEARER_TOKEN = "Bearer access-token-of-somebody-else";

  protected static final LocalStackContainer LOCALSTACK = new LocalStackContainer(LOCALSTACK_IMAGE)
      .withServices(LocalStackContainer.Service.S3)
      .withReuse(true);

  protected static final MockServerContainer LYNQ_IAM = new MockServerContainer(MOCKSERVER_IMAGE)
      .withReuse(true);

  protected static S3Client s3TestClient;
  protected static MockServerClient lynqIamMock;

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

    LYNQ_IAM.start();
    lynqIamMock = new MockServerClient(LYNQ_IAM.getHost(), LYNQ_IAM.getServerPort());
    stubUserInfo(BEARER_TOKEN, USER_ID, "janedoe", "jane@lynq.com");
    stubUserInfo(OTHER_BEARER_TOKEN, OTHER_USER_ID, "johndoe", "john@lynq.com");
  }

  private static void stubUserInfo(String bearer, String userId, String username, String email) {
    lynqIamMock.when(request()
            .withMethod("GET")
            .withPath(USERINFO_PATH)
            .withHeader(AUTHORIZATION_HEADER, bearer))
        .respond(response()
            .withStatusCode(200)
            .withContentType(MediaType.APPLICATION_JSON)
            .withBody("""
                {
                  "success": true,
                  "data": {
                    "id": "%s",
                    "username": "%s",
                    "email": "%s",
                    "roles": ["R_CANDIDATE"]
                  }
                }""".formatted(userId, username, email)));
  }

  @DynamicPropertySource
  static void registerDynamicProperties(DynamicPropertyRegistry registry) {
    registry.add("lynq.aws.endpoint", () -> LOCALSTACK.getEndpoint().toString());
    registry.add("lynq.aws.region", LOCALSTACK::getRegion);
    registry.add("lynq.aws.access-key-id", LOCALSTACK::getAccessKey);
    registry.add("lynq.aws.secret-access-key", LOCALSTACK::getSecretKey);
    registry.add("lynq.aws.bucket-name", () -> AWS_BUCKET);
    registry.add("lynq.iam.url", LYNQ_IAM::getEndpoint);
  }
}
