package com.lynq.analytics;

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

  protected static final MockServerContainer LYNQ_IAM = new MockServerContainer(MOCKSERVER_IMAGE)
      .withReuse(true);

  protected static MockServerClient lynqIamMock;

  static {
    LYNQ_IAM.start();
    lynqIamMock = new MockServerClient(LYNQ_IAM.getHost(), LYNQ_IAM.getServerPort());
  }

  @DynamicPropertySource
  static void registerDynamicProperties(DynamicPropertyRegistry registry) {
    registry.add("lynq.iam.url", LYNQ_IAM::getEndpoint);
  }
}
