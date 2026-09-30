package com.lynq.analytics;

import java.util.Map;
import org.mockserver.client.MockServerClient;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MockServerContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.CreateTopicRequest;
import software.amazon.awssdk.services.sns.model.SubscribeRequest;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesRequest;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@ActiveProfiles("test")
@Testcontainers
public abstract class AbstractE2ETest {

  private static final DockerImageName MOCKSERVER_IMAGE =
      DockerImageName.parse("mockserver/mockserver:5.15.0");

  private static final DockerImageName MYSQL_IMAGE = DockerImageName.parse("mysql:9.0");

  private static final DockerImageName LOCALSTACK_IMAGE =
      DockerImageName.parse("localstack/localstack:3.8.1");

  private static final DockerImageName REDIS_IMAGE = DockerImageName.parse("redis:7.2-alpine");

  private static final int REDIS_PORT = 6379;

  private static final String DATABASE_NAME = "lynq_analytics_db";

  protected static final String DOMAIN_EVENTS_TOPIC = "lynq-domain-events";
  protected static final String EVENTS_QUEUE = "lynq-analytics-events";
  protected static final String EVENTS_DLQ = "lynq-analytics-events-dlq";
  protected static final int MAX_RECEIVE_COUNT = 2;
  private static final String VISIBILITY_TIMEOUT_SECONDS = "1";

  protected static final MockServerContainer LYNQ_IAM = new MockServerContainer(MOCKSERVER_IMAGE)
      .withReuse(true);

  protected static final MySQLContainer<?> MYSQL = new MySQLContainer<>(MYSQL_IMAGE)
      .withDatabaseName(DATABASE_NAME)
      .withReuse(true);

  protected static final LocalStackContainer LOCALSTACK = new LocalStackContainer(LOCALSTACK_IMAGE)
      .withServices(LocalStackContainer.Service.SQS, LocalStackContainer.Service.SNS)
      .withReuse(true);

  @SuppressWarnings("resource")
  protected static final GenericContainer<?> REDIS = new GenericContainer<>(REDIS_IMAGE)
      .withExposedPorts(REDIS_PORT)
      .withReuse(true);

  protected static MockServerClient lynqIamMock;
  protected static SqsClient sqsTestClient;
  protected static SnsClient snsTestClient;
  protected static String domainEventsTopicArn;
  protected static String eventsQueueUrl;
  protected static String eventsDlqUrl;

  static {
    LYNQ_IAM.start();
    lynqIamMock = new MockServerClient(LYNQ_IAM.getHost(), LYNQ_IAM.getServerPort());

    MYSQL.start();

    REDIS.start();

    LOCALSTACK.start();
    StaticCredentialsProvider credentials = StaticCredentialsProvider.create(
        AwsBasicCredentials.create(LOCALSTACK.getAccessKey(), LOCALSTACK.getSecretKey()));
    Region region = Region.of(LOCALSTACK.getRegion());
    sqsTestClient = SqsClient.builder()
        .endpointOverride(LOCALSTACK.getEndpoint())
        .credentialsProvider(credentials)
        .region(region)
        .build();
    snsTestClient = SnsClient.builder()
        .endpointOverride(LOCALSTACK.getEndpoint())
        .credentialsProvider(credentials)
        .region(region)
        .build();
    createDomainEventsQueue();
  }

  private static void createDomainEventsQueue() {
    domainEventsTopicArn = snsTestClient.createTopic(CreateTopicRequest.builder()
        .name(DOMAIN_EVENTS_TOPIC).build()).topicArn();

    eventsDlqUrl = sqsTestClient.createQueue(CreateQueueRequest.builder()
        .queueName(EVENTS_DLQ).build()).queueUrl();
    String dlqArn = sqsTestClient.getQueueAttributes(GetQueueAttributesRequest.builder()
            .queueUrl(eventsDlqUrl)
            .attributeNames(QueueAttributeName.QUEUE_ARN)
            .build())
        .attributes().get(QueueAttributeName.QUEUE_ARN);

    eventsQueueUrl = sqsTestClient.createQueue(CreateQueueRequest.builder()
        .queueName(EVENTS_QUEUE)
        .attributes(Map.of(
            QueueAttributeName.VISIBILITY_TIMEOUT, VISIBILITY_TIMEOUT_SECONDS,
            QueueAttributeName.REDRIVE_POLICY,
            "{\"deadLetterTargetArn\":\"%s\",\"maxReceiveCount\":\"%d\"}"
                .formatted(dlqArn, MAX_RECEIVE_COUNT)))
        .build()).queueUrl();
    String queueArn = sqsTestClient.getQueueAttributes(GetQueueAttributesRequest.builder()
            .queueUrl(eventsQueueUrl)
            .attributeNames(QueueAttributeName.QUEUE_ARN)
            .build())
        .attributes().get(QueueAttributeName.QUEUE_ARN);

    snsTestClient.subscribe(SubscribeRequest.builder()
        .topicArn(domainEventsTopicArn)
        .protocol("sqs")
        .endpoint(queueArn)
        .attributes(Map.of("RawMessageDelivery", "true"))
        .build());
  }

  @DynamicPropertySource
  static void registerDynamicProperties(DynamicPropertyRegistry registry) {
    registry.add("lynq.iam.url", LYNQ_IAM::getEndpoint);
    registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
    registry.add("spring.datasource.username", MYSQL::getUsername);
    registry.add("spring.datasource.password", MYSQL::getPassword);
    registry.add("spring.cloud.aws.region.static", LOCALSTACK::getRegion);
    registry.add("spring.cloud.aws.credentials.access-key", LOCALSTACK::getAccessKey);
    registry.add("spring.cloud.aws.credentials.secret-key", LOCALSTACK::getSecretKey);
    registry.add("spring.cloud.aws.sqs.endpoint", () -> LOCALSTACK.getEndpoint().toString());
    registry.add("spring.data.redis.host", REDIS::getHost);
    registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(REDIS_PORT));
    registry.add("spring.data.redis.username", () -> "");
    registry.add("spring.data.redis.password", () -> "");
  }
}
