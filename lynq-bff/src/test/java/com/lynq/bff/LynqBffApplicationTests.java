package com.lynq.bff;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockserver.model.MediaType;
import org.mockserver.model.Parameter;
import org.mockserver.verify.VerificationTimes;
import org.springframework.boot.test.web.server.LocalServerPort;

class LynqBffApplicationTests extends AbstractE2ETest {

  private static final String CONTEXT_PATH = "/lynq-bff";

  private static final String AUTHORIZATION_HEADER = "Authorization";
  private static final String REQUEST_UUID_HEADER = "lynq-request-uuid";
  private static final String CONTENT_TYPE_HEADER = "Content-Type";
  private static final String APPLICATION_JSON = "application/json";
  private static final String REQUEST_UUID = "550e8400-e29b-41d4-a716-446655440000";

  private static final String USER_BODY = """
      {"success": true, "data": {"id": "11111111-1111-1111-1111-111111111111"}}""";

  private static final String USER_ID_HEADER = "user-id";
  private static final String COMPANY_ID_HEADER = "company-id";

  private static final String TOKEN_SUBJECT = "11111111-1111-1111-1111-111111111111";
  private static final String SPOOFED_USER_ID = "99999999-9999-9999-9999-999999999999";

  @LocalServerPort
  private int port;

  private final HttpClient httpClient = HttpClient.newHttpClient();
  private String accessToken;

  @BeforeEach
  void setUp() {
    lynqBackendMock.reset();
    lynqMlMock.reset();
    lynqFileStorageMock.reset();
    accessToken = validAccessToken();
  }

  @Test
  void proxiesToLynqBackendUnderTheDmzPrefixAndReturnsItsBodyVerbatim() throws Exception {
    lynqBackendMock.when(request().withMethod("GET").withPath("/dmz/user"))
        .respond(response().withStatusCode(200)
            .withContentType(MediaType.APPLICATION_JSON)
            .withBody(USER_BODY));

    HttpResponse<String> response = send("GET", CONTEXT_PATH + "/user", null);

    assertThat(response.statusCode(), is(200));
    assertThat(response.body(), is(USER_BODY));
    assertThat(response.headers().firstValue(CONTENT_TYPE_HEADER).orElseThrow(),
        containsString(APPLICATION_JSON));
  }

  @Test
  void forwardsTheAuthorizationAndRequestUuidHeadersToTheDmzService() throws Exception {
    lynqBackendMock.when(request().withMethod("GET").withPath("/dmz/user"))
        .respond(response().withStatusCode(200).withBody(USER_BODY));

    send("GET", CONTEXT_PATH + "/user", null);

    lynqBackendMock.verify(request()
        .withMethod("GET")
        .withPath("/dmz/user")
        .withHeader(AUTHORIZATION_HEADER, "Bearer " + accessToken)
        .withHeader(REQUEST_UUID_HEADER, REQUEST_UUID), VerificationTimes.once());
  }

  @Test
  void keepsNestedPathSegmentsAndTheQueryStringIntact() throws Exception {
    lynqBackendMock.when(request().withMethod("GET").withPath("/dmz/user/generate-upload-image"))
        .respond(response().withStatusCode(200).withBody("{}"));

    HttpResponse<String> response = send(
        "GET", CONTEXT_PATH + "/user/generate-upload-image?file-name=avatar.png", null);

    assertThat(response.statusCode(), is(200));
    lynqBackendMock.verify(request()
        .withMethod("GET")
        .withPath("/dmz/user/generate-upload-image")
        .withQueryStringParameter(Parameter.param("file-name", "avatar.png")),
        VerificationTimes.once());
  }

  @Test
  void keepsRepeatedQueryParametersIntact() throws Exception {
    lynqBackendMock.when(request().withMethod("GET").withPath("/dmz/job"))
        .respond(response().withStatusCode(200).withBody("{}"));

    send("GET", CONTEXT_PATH + "/job?skills=Java&skills=Spring", null);

    lynqBackendMock.verify(request()
        .withMethod("GET")
        .withPath("/dmz/job")
        .withQueryStringParameter(Parameter.param("skills", "Java", "Spring")),
        VerificationTimes.once());
  }

  @Test
  void passesThePostBodyThroughUnchanged() throws Exception {
    String requestBody = """
        {"title": "Senior Backend Engineer", "workType": "REMOTE"}""";
    lynqBackendMock.when(request().withMethod("POST").withPath("/dmz/job"))
        .respond(response().withStatusCode(201).withBody(USER_BODY));

    HttpResponse<String> response = send("POST", CONTEXT_PATH + "/job", requestBody);

    assertThat(response.statusCode(), is(201));
    lynqBackendMock.verify(request()
        .withMethod("POST")
        .withPath("/dmz/job")
        .withBody(requestBody), VerificationTimes.once());
  }

  @Test
  void proxiesPatchWhichFeignsDefaultHttpUrlConnectionClientCouldNotIssue() throws Exception {
    String requestBody = """
        {"fullName": "Jane Q. Doe"}""";
    lynqBackendMock.when(request().withMethod("PATCH").withPath("/dmz/user"))
        .respond(response().withStatusCode(200).withBody(USER_BODY));

    HttpResponse<String> response = send("PATCH", CONTEXT_PATH + "/user", requestBody);

    assertThat(response.statusCode(), is(200));
    assertThat(response.body(), is(USER_BODY));
    lynqBackendMock.verify(request()
        .withMethod("PATCH")
        .withPath("/dmz/user")
        .withBody(requestBody), VerificationTimes.once());
  }

  @Test
  void proxiesDeleteAndKeepsABodilessAnswerBodiless() throws Exception {
    lynqFileStorageMock.when(request().withMethod("DELETE").withPath("/dmz/files/some-file-id"))
        .respond(response().withStatusCode(204));

    HttpResponse<String> response =
        send("DELETE", CONTEXT_PATH + "/files/some-file-id", null);

    assertThat(response.statusCode(), is(204));
    assertThat(response.body(), is(""));
  }

  @Test
  void routesToLynqMlOnTheMlPrefix() throws Exception {
    String mlBody = """
        {"success": true, "data": {"language": "en"}}""";
    lynqMlMock.when(request().withMethod("POST").withPath("/dmz/detect-language"))
        .respond(response().withStatusCode(200)
            .withContentType(MediaType.APPLICATION_JSON)
            .withBody(mlBody));

    HttpResponse<String> response = send("POST", CONTEXT_PATH + "/detect-language", "{}");

    assertThat(response.statusCode(), is(200));
    assertThat(response.body(), is(mlBody));
    lynqBackendMock.verify(request(), VerificationTimes.exactly(0));
  }

  @Test
  void relaysSkillEnhanceStraightToLynqMlWithoutGoingThroughLynqBackend() throws Exception {
    String mlBody = """
        {"success": true, "data": {"skills": ["Java", "Spring"]}}""";
    lynqMlMock.when(request().withMethod("POST").withPath("/dmz/skill-enhance"))
        .respond(response().withStatusCode(200)
            .withContentType(MediaType.APPLICATION_JSON)
            .withBody(mlBody));

    HttpResponse<String> response = send("POST", CONTEXT_PATH + "/skill-enhance", "{}");

    assertThat(response.statusCode(), is(200));
    assertThat(response.body(), is(mlBody));
    lynqMlMock.verify(request()
        .withPath("/dmz/skill-enhance")
        .withHeader(USER_ID_HEADER, TOKEN_SUBJECT), VerificationTimes.once());
    lynqBackendMock.verify(request(), VerificationTimes.exactly(0));
  }

  @Test
  void relaysResumeSkillExtractionKeepingTheMultiSegmentPathAndLanguageParam() throws Exception {
    String mlBody = """
        {"success": true, "data": {"skills": ["Java"], "tools": [], "soft": []}}""";
    lynqMlMock.when(request().withMethod("POST").withPath("/dmz/resume/skill-extraction"))
        .respond(response().withStatusCode(200)
            .withContentType(MediaType.APPLICATION_JSON)
            .withBody(mlBody));

    HttpResponse<String> response =
        send("POST", CONTEXT_PATH + "/resume/skill-extraction?language=es", "{}");

    assertThat(response.statusCode(), is(200));
    assertThat(response.body(), is(mlBody));
    lynqMlMock.verify(request()
        .withPath("/dmz/resume/skill-extraction")
        .withQueryStringParameter(Parameter.param("language", "es"))
        .withHeader(USER_ID_HEADER, TOKEN_SUBJECT), VerificationTimes.once());
    lynqBackendMock.verify(request(), VerificationTimes.exactly(0));
  }

  @Test
  void refusesTheLynqMlEvaluationsThatLynqBackendOwns() throws Exception {
    for (String endpoint : new String[] {"upskilling_suggestion", "candidate-explanation"}) {
      HttpResponse<String> response = send("POST", CONTEXT_PATH + "/" + endpoint, "{}");

      assertThat(response.statusCode(), is(403));
      assertThat(response.body(), containsString("built from lynq-backend's data"));
    }
    lynqMlMock.verify(request(), VerificationTimes.exactly(0));
  }

  @Test
  void refusesTheLynqMlEndpointsThatFetchACallerSuppliedUrl() throws Exception {
    for (String endpoint : new String[] {"parse-resume", "resume-template-creation"}) {
      HttpResponse<String> response = send("POST", CONTEXT_PATH + "/" + endpoint, "{}");

      assertThat(response.statusCode(), is(403));
      assertThat(response.body(), containsString("caller-supplied URL"));
    }
    lynqMlMock.verify(request(), VerificationTimes.exactly(0));
  }

  @Test
  void forwardsTheTokenSubjectAsTheUserIdHeader() throws Exception {
    lynqBackendMock.when(request().withMethod("GET").withPath("/dmz/user"))
        .respond(response().withStatusCode(200).withBody(USER_BODY));

    send("GET", CONTEXT_PATH + "/user", null);

    lynqBackendMock.verify(request()
        .withPath("/dmz/user")
        .withHeader(USER_ID_HEADER, TOKEN_SUBJECT), VerificationTimes.once());
  }

  @Test
  void overwritesAClientSuppliedUserIdWithTheTokenSubject() throws Exception {
    lynqBackendMock.when(request().withMethod("GET").withPath("/dmz/user"))
        .respond(response().withStatusCode(200).withBody(USER_BODY));

    HttpRequest httpRequest = HttpRequest.newBuilder()
        .uri(URI.create(baseUrl() + CONTEXT_PATH + "/user"))
        .header(AUTHORIZATION_HEADER, "Bearer " + accessToken)
        .header(REQUEST_UUID_HEADER, REQUEST_UUID)
        .header(USER_ID_HEADER, SPOOFED_USER_ID)
        .header(COMPANY_ID_HEADER, "22222222-2222-2222-2222-222222222222")
        .GET()
        .build();
    httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

    lynqBackendMock.verify(request()
        .withPath("/dmz/user")
        .withHeader(USER_ID_HEADER, TOKEN_SUBJECT), VerificationTimes.once());
    lynqBackendMock.verify(request()
        .withPath("/dmz/user")
        .withHeader(USER_ID_HEADER, SPOOFED_USER_ID), VerificationTimes.exactly(0));
    lynqBackendMock.verify(request()
        .withPath("/dmz/user")
        .withHeader(COMPANY_ID_HEADER), VerificationTimes.exactly(0));
  }

  @Test
  void routesToLynqFileStorageOnTheFileStoragePrefix() throws Exception {
    lynqFileStorageMock.when(request().withMethod("POST").withPath("/dmz/files/upload-url"))
        .respond(response().withStatusCode(201).withBody("{}"));

    HttpResponse<String> response =
        send("POST", CONTEXT_PATH + "/files/upload-url", "{}");

    assertThat(response.statusCode(), is(201));
    lynqFileStorageMock.verify(request()
        .withMethod("POST")
        .withPath("/dmz/files/upload-url"), VerificationTimes.once());
  }

  @Test
  void passesADownstreamErrorStatusAndBodyStraightBack() throws Exception {
    String errorBody = """
        {"success": false, "reason": "User not found"}""";
    lynqBackendMock.when(request().withMethod("GET").withPath("/dmz/user"))
        .respond(response().withStatusCode(404)
            .withContentType(MediaType.APPLICATION_JSON)
            .withBody(errorBody));

    HttpResponse<String> response = send("GET", CONTEXT_PATH + "/user", null);

    assertThat(response.statusCode(), is(404));
    assertThat(response.body(), is(errorBody));
  }

  @Test
  void echoesTheRequestUuidHeaderExactlyOnce() throws Exception {
    lynqBackendMock.when(request().withMethod("GET").withPath("/dmz/user"))
        .respond(response().withStatusCode(200)
            .withHeader(REQUEST_UUID_HEADER, REQUEST_UUID)
            .withBody(USER_BODY));

    HttpResponse<String> response = send("GET", CONTEXT_PATH + "/user", null);

    assertThat(response.headers().allValues(REQUEST_UUID_HEADER).size(), is(1));
    assertThat(response.headers().firstValue(REQUEST_UUID_HEADER).orElseThrow(), is(REQUEST_UUID));
  }

  @Test
  void returnsUnauthorizedWhenTheAuthorizationHeaderIsMissing() throws Exception {
    HttpRequest httpRequest = HttpRequest.newBuilder()
        .uri(URI.create(baseUrl() + CONTEXT_PATH + "/user"))
        .header(REQUEST_UUID_HEADER, REQUEST_UUID)
        .GET()
        .build();

    HttpResponse<String> response =
        httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

    assertThat(response.statusCode(), is(401));
    assertThat(response.body(), containsString("Missing Authorization header"));
    lynqBackendMock.verify(request(), VerificationTimes.exactly(0));
  }

  @Test
  void returnsUnauthorizedWhenTheTokenWasSignedWithAnotherSecret() throws Exception {
    accessToken = accessToken(
        "0000000000000000000000000000000000000000000000000000000000000000",
        Instant.now().plus(15, ChronoUnit.MINUTES));

    HttpResponse<String> response = send("GET", CONTEXT_PATH + "/user", null);

    assertThat(response.statusCode(), is(401));
    assertThat(response.body(), containsString("Invalid or expired access token"));
    lynqBackendMock.verify(request(), VerificationTimes.exactly(0));
  }

  @Test
  void returnsUnauthorizedWhenTheTokenHasExpired() throws Exception {
    accessToken = accessToken(JWT_SECRET, Instant.now().minus(1, ChronoUnit.MINUTES));

    HttpResponse<String> response = send("GET", CONTEXT_PATH + "/user", null);

    assertThat(response.statusCode(), is(401));
    lynqBackendMock.verify(request(), VerificationTimes.exactly(0));
  }

  @Test
  void returnsForbiddenWhenTheRequestUuidHeaderIsMissing() throws Exception {
    HttpRequest httpRequest = HttpRequest.newBuilder()
        .uri(URI.create(baseUrl() + CONTEXT_PATH + "/user"))
        .header(AUTHORIZATION_HEADER, "Bearer " + accessToken)
        .GET()
        .build();

    HttpResponse<String> response =
        httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

    assertThat(response.statusCode(), is(403));
    lynqBackendMock.verify(request(), VerificationTimes.exactly(0));
  }

  @Test
  void returnsMethodNotAllowedForAVerbTheGatewayDoesNotRelay() throws Exception {
    HttpResponse<String> response = send("HEAD", CONTEXT_PATH + "/user", null);

    assertThat(response.statusCode(), is(405));
    lynqBackendMock.verify(request(), VerificationTimes.exactly(0));
  }

  @Test
  void passesAnUnstubbedDownstreamPathBackAsItsOwn404() throws Exception {
    HttpResponse<String> response = send("GET", CONTEXT_PATH + "/user/unknown-endpoint", null);

    assertThat(response.statusCode(), is(404));
    lynqBackendMock.verify(request().withPath("/dmz/user/unknown-endpoint"),
        VerificationTimes.once());
  }

  @Test
  void answers404ForAResourceTheGatewayDoesNotRoute() throws Exception {
    HttpResponse<String> response = send("GET", CONTEXT_PATH + "/not-a-resource", null);

    assertThat(response.statusCode(), is(404));
    lynqBackendMock.verify(request(), VerificationTimes.exactly(0));
    lynqMlMock.verify(request(), VerificationTimes.exactly(0));
    lynqFileStorageMock.verify(request(), VerificationTimes.exactly(0));
  }

  private String baseUrl() {
    return "http://localhost:" + port;
  }

  private HttpResponse<String> send(String method, String path, String body) throws Exception {
    HttpRequest.BodyPublisher publisher = body == null
        ? HttpRequest.BodyPublishers.noBody()
        : HttpRequest.BodyPublishers.ofString(body);

    HttpRequest.Builder builder = HttpRequest.newBuilder()
        .uri(URI.create(baseUrl() + path))
        .header(AUTHORIZATION_HEADER, "Bearer " + accessToken)
        .header(REQUEST_UUID_HEADER, REQUEST_UUID)
        .method(method, publisher);

    if (body != null) {
      builder.header(CONTENT_TYPE_HEADER, APPLICATION_JSON);
    }

    return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
  }
}
