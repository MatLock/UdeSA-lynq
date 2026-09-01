package com.lynq.bff;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestPropertySource(properties = {
    "lynq.backend.url=http://localhost:1/lynq-backend-app"
})
class DmzUnreachableTest {

  private static final String AUTHORIZATION_HEADER = "Authorization";
  private static final String REQUEST_UUID_HEADER = "lynq-request-uuid";
  private static final String REQUEST_UUID = "550e8400-e29b-41d4-a716-446655440000";
  private static final String JWT_SECRET =
      "8d2b6d2a9c5e8f4f8f9d1c7a4e3b5f6d8c9a1e2f3a4b5c6d7e8f9a1b2c3d4e5";

  @LocalServerPort
  private int port;

  private final HttpClient httpClient = HttpClient.newHttpClient();

  @Test
  void returnsBadGatewayWhenTheDmzServiceCannotBeReached() throws Exception {
    HttpRequest httpRequest = HttpRequest.newBuilder()
        .uri(URI.create("http://localhost:" + port + "/lynq-bff/user"))
        .header(AUTHORIZATION_HEADER, "Bearer " + validAccessToken())
        .header(REQUEST_UUID_HEADER, REQUEST_UUID)
        .GET()
        .build();

    HttpResponse<String> response =
        httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

    assertThat(response.statusCode(), is(502));
    assertThat(response.body(), containsString("Downstream service is unavailable"));
  }

  private static String validAccessToken() {
    return Jwts.builder()
        .subject("11111111-1111-1111-1111-111111111111")
        .issuedAt(Date.from(Instant.now()))
        .expiration(Date.from(Instant.now().plus(15, ChronoUnit.MINUTES)))
        .signWith(Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8)))
        .compact();
  }
}
