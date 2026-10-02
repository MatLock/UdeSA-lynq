package com.lynq.analytics;

import com.lynq.analytics.enums.JobStatus;
import com.lynq.analytics.model.ApplicationEntity;
import com.lynq.analytics.model.JobPostEntity;
import com.lynq.analytics.repository.ApplicationRepository;
import com.lynq.analytics.repository.JobPostRepository;
import com.lynq.analytics.security.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockserver.model.MediaType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

class StandingApplicationTests extends AbstractE2ETest {

  private static final String CONTEXT_PATH = "/lynq-analytics";
  private static final String USERINFO_PATH = "/auth/user-info";
  private static final String AUTHORIZATION_HEADER = "Authorization";
  private static final String REQUEST_UUID_HEADER = "lynq-request-uuid";
  private static final String BEARER_TOKEN = "Bearer test-access-token";
  private static final String REQUEST_UUID = "550e8400-e29b-41d4-a716-446655440000";

  private static final String JOB_ID = "77777777-7777-7777-7777-777777777777";
  private static final String UNKNOWN_JOB_ID = "88888888-8888-8888-8888-888888888888";
  private static final String USER_ID = "11111111-1111-1111-1111-111111111111";
  private static final String NEWCOMER_ID = "99999999-9999-9999-9999-999999999999";
  private static final Instant OCCURRED_ON = Instant.parse("2026-09-20T10:00:00Z");

  @LocalServerPort
  private int port;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private JobPostRepository jobPostRepository;

  @Autowired
  private ApplicationRepository applicationRepository;

  @Autowired
  private StringRedisTemplate redisTemplate;

  private final HttpClient httpClient = HttpClient.newHttpClient();

  @BeforeEach
  void setUp() {
    lynqIamMock.reset();
    Set<String> keys = redisTemplate.keys("lynq-analytics::*");
    if (!keys.isEmpty()) {
      redisTemplate.delete(keys);
    }
    applicationRepository.deleteAll();
    jobPostRepository.deleteAll();
    jobPostRepository.save(jobPost());
  }

  @Test
  void answersTheRankOfTheCallerWithTiesSharingItAndTheMedianOfTheJobPost() throws Exception {
    givenApplications(Map.of(USER_ID, 72, "a1", 90, "a2", 72, "a3", 60, "a4", 40, "a5", 30));
    stubIamUserInfo(Role.CANDIDATE);

    HttpResponse<String> response = getStanding(JOB_ID);

    assertThat(response.statusCode(), is(200));
    Map<String, Object> data = data(response);
    assertThat(data.get("rank"), is(2));
    assertThat(data.get("totalApplicants"), is(6));
    assertThat(((Number) data.get("percentile")).doubleValue(),
        closeTo(100.0 * (3 + 1) / 6, 1e-9));
    assertThat(data.get("score"), is(72));
    assertThat(data.get("medianScore"), is(66.0));
    assertThat(data.size(), is(5));
  }

  @Test
  void withholdsTheMedianBelowFiveApplicants() throws Exception {
    givenApplications(Map.of(USER_ID, 72, "a1", 40));
    stubIamUserInfo(Role.CANDIDATE);

    Map<String, Object> data = data(getStanding(JOB_ID));

    assertThat(data.get("rank"), is(1));
    assertThat(data.get("totalApplicants"), is(2));
    assertThat(data.get("medianScore"), is(nullValue()));
  }

  @Test
  void servesTheCachedStandingUntilItExpires() throws Exception {
    givenApplications(Map.of(USER_ID, 72, "a1", 40));
    stubIamUserInfo(Role.CANDIDATE);
    getStanding(JOB_ID);

    applicationRepository.save(application(NEWCOMER_ID, 95));
    Map<String, Object> data = data(getStanding(JOB_ID));

    assertThat(redisTemplate.hasKey("lynq-analytics::standing::" + JOB_ID + ":" + USER_ID),
        is(true));
    assertThat(data.get("rank"), is(1));
    assertThat(data.get("totalApplicants"), is(2));
  }

  @Test
  void refusesACandidateWhoDidNotApply() throws Exception {
    givenApplications(Map.of("a1", 90));
    stubIamUserInfo(Role.CANDIDATE);

    HttpResponse<String> response = getStanding(JOB_ID);

    assertThat(response.statusCode(), is(403));
    assertThat(parse(response.body()).get("reason"),
        is("Only a candidate who applied to the job post can read their standing"));
    assertThat(redisTemplate.keys("lynq-analytics::standing::*").isEmpty(), is(true));
  }

  @Test
  void refusesACompany() throws Exception {
    givenApplications(Map.of(USER_ID, 72));
    stubIamUserInfo(Role.COMPANY);

    HttpResponse<String> response = getStanding(JOB_ID);

    assertThat(response.statusCode(), is(403));
    assertThat(parse(response.body()).get("reason"),
        is("Only users of type CANDIDATE can perform this action"));
  }

  @Test
  void answersNotFoundForAJobPostAnalyticsDoesNotHold() throws Exception {
    stubIamUserInfo(Role.CANDIDATE);

    HttpResponse<String> response = getStanding(UNKNOWN_JOB_ID);

    assertThat(response.statusCode(), is(404));
    assertThat(parse(response.body()).get("reason"),
        is("Job post '" + UNKNOWN_JOB_ID + "' not found"));
  }

  private void givenApplications(Map<String, Integer> scoresByCandidate) {
    scoresByCandidate.forEach((candidateId, score) ->
        applicationRepository.save(application(candidateId, score)));
  }

  private static ApplicationEntity application(String candidateId, int score) {
    return ApplicationEntity.builder()
        .id(UUID.randomUUID().toString())
        .jobId(JOB_ID)
        .candidateId(candidateId)
        .appliedOn(LocalDate.parse("2026-09-22"))
        .lynqScore(score)
        .occurredOn(OCCURRED_ON)
        .build();
  }

  private static JobPostEntity jobPost() {
    return JobPostEntity.builder()
        .id(JOB_ID)
        .title("Backend Developer")
        .workType("REMOTE")
        .source("LYNQ")
        .status(JobStatus.OPEN)
        .publishedOn(LocalDate.parse("2026-09-20"))
        .detailsOccurredOn(OCCURRED_ON)
        .statusOccurredOn(OCCURRED_ON)
        .build();
  }

  private HttpResponse<String> getStanding(String jobId) throws Exception {
    URI uri = URI.create("http://localhost:" + port + CONTEXT_PATH + "/dmz/analytics/job/"
        + jobId + "/standing");
    return httpClient.send(HttpRequest.newBuilder(uri)
            .header(AUTHORIZATION_HEADER, BEARER_TOKEN)
            .header(REQUEST_UUID_HEADER, REQUEST_UUID)
            .GET()
            .build(),
        HttpResponse.BodyHandlers.ofString());
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> data(HttpResponse<String> response) {
    return (Map<String, Object>) parse(response.body()).get("data");
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> parse(String json) {
    return objectMapper.readValue(json, Map.class);
  }

  private void stubIamUserInfo(String role) {
    lynqIamMock.when(request().withMethod("GET").withPath(USERINFO_PATH))
        .respond(response()
            .withStatusCode(200)
            .withContentType(MediaType.APPLICATION_JSON)
            .withBody("""
                {
                  "success": true,
                  "data": {
                    "id": "%s",
                    "username": "janedoe",
                    "email": "jane@lynq.com",
                    "roles": ["%s"]
                  }
                }""".formatted(USER_ID, Role.PREFIX + role)));
  }
}
