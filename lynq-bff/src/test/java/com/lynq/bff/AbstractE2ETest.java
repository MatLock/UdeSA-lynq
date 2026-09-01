package com.lynq.bff;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
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

  protected static final String JWT_SECRET =
      "8d2b6d2a9c5e8f4f8f9d1c7a4e3b5f6d8c9a1e2f3a4b5c6d7e8f9a1b2c3d4e5";

  protected static final MockServerContainer LYNQ_BACKEND =
      new MockServerContainer(MOCKSERVER_IMAGE).withReuse(true);

  protected static final MockServerContainer LYNQ_ML =
      new MockServerContainer(MOCKSERVER_IMAGE).withReuse(true);

  protected static final MockServerContainer LYNQ_FILE_STORAGE =
      new MockServerContainer(MOCKSERVER_IMAGE).withReuse(true);

  protected static MockServerClient lynqBackendMock;
  protected static MockServerClient lynqMlMock;
  protected static MockServerClient lynqFileStorageMock;

  static {
    LYNQ_BACKEND.start();
    lynqBackendMock = new MockServerClient(LYNQ_BACKEND.getHost(), LYNQ_BACKEND.getServerPort());

    LYNQ_ML.start();
    lynqMlMock = new MockServerClient(LYNQ_ML.getHost(), LYNQ_ML.getServerPort());

    LYNQ_FILE_STORAGE.start();
    lynqFileStorageMock =
        new MockServerClient(LYNQ_FILE_STORAGE.getHost(), LYNQ_FILE_STORAGE.getServerPort());
  }

  @DynamicPropertySource
  static void registerDynamicProperties(DynamicPropertyRegistry registry) {
    registry.add("lynq.backend.url", LYNQ_BACKEND::getEndpoint);
    registry.add("lynq.ml.url", LYNQ_ML::getEndpoint);
    registry.add("lynq.file-storage.url", LYNQ_FILE_STORAGE::getEndpoint);
  }

  protected static String validAccessToken() {
    return accessToken(JWT_SECRET, Instant.now().plus(15, ChronoUnit.MINUTES));
  }

  protected static String accessToken(String secret, Instant expiration) {
    SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    return Jwts.builder()
        .id(UUID.randomUUID().toString())
        .subject("11111111-1111-1111-1111-111111111111")
        .claim("username", "janedoe")
        .claim("email", "jane@lynq.com")
        .issuedAt(Date.from(Instant.now().minus(1, ChronoUnit.MINUTES)))
        .expiration(Date.from(expiration))
        .signWith(key)
        .compact();
  }
}
