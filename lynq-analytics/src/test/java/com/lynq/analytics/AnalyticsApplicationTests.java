package com.lynq.analytics;

import com.lynq.analytics.security.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockserver.model.MediaType;
import org.mockserver.verify.VerificationTimes;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

class AnalyticsApplicationTests extends AbstractE2ETest {

  private static final String CONTEXT_PATH = "/lynq-analytics";
  private static final String ANALYTICS_PATH = "/dmz/analytics/job/00000000-0000-0000-0000-000000000000/standing";
  private static final String API_DOCS_PATH = "/v3/api-docs";
  private static final String USERINFO_PATH = "/auth/user-info";

  private static final String AUTHORIZATION_HEADER = "Authorization";
  private static final String REQUEST_UUID_HEADER = "lynq-request-uuid";
  private static final String BEARER_TOKEN = "Bearer test-access-token";
  private static final String REQUEST_UUID = "550e8400-e29b-41d4-a716-446655440000";

  private static final String USER_ID = "11111111-1111-1111-1111-111111111111";
  private static final String USERNAME = "janedoe";
  private static final String EMAIL = "jane@lynq.com";

  private static final String MISSING_REQUEST_UUID_REASON = "Missing required header";
  private static final String MISSING_AUTHORIZATION_REASON = "Missing Authorization header";
  private static final String INVALID_TOKEN_REASON = "Invalid or expired access token";
  private static final String IAM_UNAVAILABLE_REASON = "Authentication service is unavailable";

  @LocalServerPort
  private int port;

  @Autowired
  private ObjectMapper objectMapper;

  private final HttpClient httpClient = HttpClient.newHttpClient();

  @BeforeEach
  void setUp() {
    lynqIamMock.reset();
  }

  @Test
  void rejectsARequestWithoutRequestUuidAsForbidden() throws Exception {
    HttpResponse<String> response = send(HttpRequest.newBuilder(analyticsUri())
        .header(AUTHORIZATION_HEADER, BEARER_TOKEN));

    assertThat(response.statusCode(), is(403));
    assertThat(parse(response.body()).get("success"), is(false));
    assertThat(parse(response.body()).get("reason"), is(MISSING_REQUEST_UUID_REASON));
    lynqIamMock.verify(request().withPath(USERINFO_PATH), VerificationTimes.never());
  }

  @Test
  void rejectsARequestWithoutAuthorizationAsUnauthorized() throws Exception {
    HttpResponse<String> response = send(HttpRequest.newBuilder(analyticsUri())
        .header(REQUEST_UUID_HEADER, REQUEST_UUID));

    assertThat(response.statusCode(), is(401));
    assertThat(parse(response.body()).get("reason"), is(MISSING_AUTHORIZATION_REASON));
    lynqIamMock.verify(request().withPath(USERINFO_PATH), VerificationTimes.never());
  }

  @Test
  void rejectsATokenThatIamDoesNotRecognizeAsUnauthorized() throws Exception {
    stubIamUserInfoStatus(401);

    HttpResponse<String> response = sendAuthenticated();

    assertThat(response.statusCode(), is(401));
    assertThat(parse(response.body()).get("reason"), is(INVALID_TOKEN_REASON));
  }

  @Test
  void answersServiceUnavailableWhenIamFails() throws Exception {
    stubIamUserInfoStatus(500);

    HttpResponse<String> response = sendAuthenticated();

    assertThat(response.statusCode(), is(503));
    assertThat(parse(response.body()).get("reason"), is(IAM_UNAVAILABLE_REASON));
  }

  @Test
  void letsAnAuthenticatedRequestThroughToTheDispatcherAndEchoesTheRequestUuid() throws Exception {
    stubIamUserInfo();

    HttpResponse<String> response = sendAuthenticated();

    assertThat(response.statusCode(), is(404));
    assertThat(response.headers().firstValue(REQUEST_UUID_HEADER).orElse(null), is(REQUEST_UUID));
    lynqIamMock.verify(request()
            .withMethod("GET")
            .withPath(USERINFO_PATH)
            .withHeader(AUTHORIZATION_HEADER, BEARER_TOKEN)
            .withHeader(REQUEST_UUID_HEADER, REQUEST_UUID),
        VerificationTimes.once());
  }

  @Test
  void servesTheApiDocsWithoutAnyHeader() throws Exception {
    HttpResponse<String> response = send(HttpRequest.newBuilder(
        URI.create("http://localhost:" + port + CONTEXT_PATH + API_DOCS_PATH)));

    assertThat(response.statusCode(), is(200));
    lynqIamMock.verify(request().withPath(USERINFO_PATH), VerificationTimes.never());
  }

  private HttpResponse<String> sendAuthenticated() throws Exception {
    return send(HttpRequest.newBuilder(analyticsUri())
        .header(AUTHORIZATION_HEADER, BEARER_TOKEN)
        .header(REQUEST_UUID_HEADER, REQUEST_UUID));
  }

  private HttpResponse<String> send(HttpRequest.Builder builder) throws Exception {
    return httpClient.send(builder.GET().build(), HttpResponse.BodyHandlers.ofString());
  }

  private URI analyticsUri() {
    return URI.create("http://localhost:" + port + CONTEXT_PATH + ANALYTICS_PATH);
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> parse(String json) {
    return objectMapper.readValue(json, Map.class);
  }

  private void stubIamUserInfo() {
    lynqIamMock.when(request().withMethod("GET").withPath(USERINFO_PATH))
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
                    "roles": ["%s"]
                  }
                }""".formatted(USER_ID, USERNAME, EMAIL, Role.PREFIX + Role.CANDIDATE)));
  }

  private void stubIamUserInfoStatus(int status) {
    lynqIamMock.when(request().withMethod("GET").withPath(USERINFO_PATH))
        .respond(response()
            .withStatusCode(status)
            .withContentType(MediaType.APPLICATION_JSON)
            .withBody("""
                {"success": false, "reason": "error"}"""));
  }
}
