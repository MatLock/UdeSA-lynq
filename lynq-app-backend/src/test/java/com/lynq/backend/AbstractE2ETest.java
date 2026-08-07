package com.lynq.backend;

import org.mockserver.client.MockServerClient;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MockServerContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("test")
@Testcontainers
public abstract class AbstractE2ETest {

  private static final DockerImageName MOCKSERVER_IMAGE =
      DockerImageName.parse("mockserver/mockserver:5.15.0");

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
   * Stands in for the lynq-file-storage service, which owns every stored file: this service holds
   * only the file ids it hands back. Tests register expectations on its {@code /files} endpoints
   * through {@link #lynqFileStorageMock}, so no bucket is involved anywhere in this suite.
   */
  protected static final MockServerContainer LYNQ_FILE_STORAGE =
      new MockServerContainer(MOCKSERVER_IMAGE).withReuse(true);

  protected static MockServerClient lynqIamMock;
  protected static MockServerClient lynqMlMock;
  protected static MockServerClient lynqFileStorageMock;

  static {
    LYNQ_IAM.start();
    lynqIamMock = new MockServerClient(LYNQ_IAM.getHost(), LYNQ_IAM.getServerPort());

    LYNQ_ML.start();
    lynqMlMock = new MockServerClient(LYNQ_ML.getHost(), LYNQ_ML.getServerPort());

    LYNQ_FILE_STORAGE.start();
    lynqFileStorageMock =
        new MockServerClient(LYNQ_FILE_STORAGE.getHost(), LYNQ_FILE_STORAGE.getServerPort());
  }

  @DynamicPropertySource
  static void registerDynamicProperties(DynamicPropertyRegistry registry) {
    registry.add("lynq.iam.url", LYNQ_IAM::getEndpoint);
    registry.add("lynq.ml.url", LYNQ_ML::getEndpoint);
    registry.add("lynq.file-storage.url", LYNQ_FILE_STORAGE::getEndpoint);
  }
}
