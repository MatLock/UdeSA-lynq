package com.lynq.analytics;

import com.lynq.analytics.enums.JobStatus;
import com.lynq.analytics.model.JobPostEntity;
import com.lynq.analytics.repository.ApplicationRepository;
import com.lynq.analytics.repository.CandidateRepository;
import com.lynq.analytics.repository.JobPostRepository;
import com.lynq.analytics.repository.TagFrequencyRepository;
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
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

class TimeToFillApplicationTests extends AbstractE2ETest {

  private static final String CONTEXT_PATH = "/lynq-analytics";
  private static final String USERINFO_PATH = "/auth/user-info";
  private static final String REQUEST_UUID = "550e8400-e29b-41d4-a716-446655440000";
  private static final String OWNER_ID = "11111111-1111-1111-1111-111111111111";
  private static final String OTHER_COMPANY_ID = "22222222-2222-2222-2222-222222222222";
  private static final String JOB_ID = "REF";
  private static final String UNKNOWN_JOB_ID = "88888888-8888-8888-8888-888888888888";
  private static final Set<String> TAGS = Set.of("backend", "sql", "cloud");
  private static final Set<String> OTHER_TAGS = Set.of("sales", "retail");
  private static final LocalDate PUBLISHED_ON = LocalDate.parse("2026-08-01");
  private static final Instant OCCURRED_ON = Instant.parse("2026-09-20T10:00:00Z");

  @LocalServerPort
  private int port;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private ApplicationRepository applicationRepository;

  @Autowired
  private JobPostRepository jobPostRepository;

  @Autowired
  private CandidateRepository candidateRepository;

  @Autowired
  private TagFrequencyRepository tagFrequencyRepository;

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
    candidateRepository.deleteAll();
    tagFrequencyRepository.deleteAll();
    JobPostEntity reference = jobPost(JOB_ID, "LYNQ", TAGS, null, null);
    reference.setCreatedByUserId(OWNER_ID);
    jobPostRepository.save(reference);
    jobPostRepository.save(jobPost("S1", "BUMERAN", TAGS, 10, "VERIFIED_GONE"));
    jobPostRepository.save(jobPost("S2", "COMPUTRABAJO", TAGS, 14, "VERIFIED_GONE"));
    jobPostRepository.save(jobPost("S3", "LYNQ", TAGS, 21, "OWNER"));
    jobPostRepository.save(jobPost("S4", "BUMERAN", TAGS, 25, "VERIFIED_CLOSED"));
    jobPostRepository.save(jobPost("S5", "COMPUTRABAJO", TAGS, 30, "VERIFIED_GONE"));
    jobPostRepository.save(jobPost("EXPIRED", "BUMERAN", TAGS, 40, "EXPIRED_BY_POLICY"));
    jobPostRepository.save(jobPost("OPEN", "BUMERAN", TAGS, null, null));
    jobPostRepository.save(jobPost("UNRELATED", "LYNQ", OTHER_TAGS, 90, "OWNER"));
  }

  @Test
  void answersTheDaysSimilarPostsTookToCloseToTheCompanyThatPublishedIt() throws Exception {
    stubIamUserInfo(OWNER_ID, Role.COMPANY);

    HttpResponse<String> response = getTimeToFill(JOB_ID);

    assertThat(response.statusCode(), is(200));
    Map<String, Object> data = data(response);
    assertThat(data.get("median"), is(21.0));
    assertThat(data.get("p25"), is(14.0));
    assertThat(data.get("p75"), is(25.0));
    assertThat(data.get("n"), is(5));
    assertThat(data.get("insufficientData"), is(false));
    assertThat(data.get("externalJobPosts"), is(4));
    assertThat(data.get("expiredByPolicy"), is(1));
    assertThat(data.get("expiredAfterDays"), is(25));
    assertThat(data.get("overall"), is(nullValue()));
    assertThat(redisTemplate.hasKey("lynq-analytics::time-to-fill::" + JOB_ID + ":" + OWNER_ID),
        is(true));
  }

  @Test
  void fallsBackToEveryClosedPostWhenTooFewSimilarOnesClosed() throws Exception {
    jobPostRepository.deleteAllById(Set.of("S2", "S3", "S4", "S5"));
    stubIamUserInfo(OWNER_ID, Role.COMPANY);

    Map<String, Object> data = data(getTimeToFill(JOB_ID));

    assertThat(data.get("insufficientData"), is(true));
    assertThat(data.get("median"), is(nullValue()));
    assertThat(data.get("n"), is(1));
    @SuppressWarnings("unchecked")
    Map<String, Object> overall = (Map<String, Object>) data.get("overall");
    assertThat(overall.get("n"), is(2));
    assertThat(overall.get("insufficientData"), is(true));
  }

  @Test
  void refusesAnotherCompany() throws Exception {
    stubIamUserInfo(OTHER_COMPANY_ID, Role.COMPANY);

    HttpResponse<String> response = getTimeToFill(JOB_ID);

    assertThat(response.statusCode(), is(403));
    assertThat(parse(response.body()).get("reason"),
        is("Only the company that published the job post can read its time to fill"));
  }

  @Test
  void refusesACandidate() throws Exception {
    stubIamUserInfo(OWNER_ID, Role.CANDIDATE);

    assertThat(getTimeToFill(JOB_ID).statusCode(), is(403));
  }

  @Test
  void answersNotFoundForAJobPostAnalyticsDoesNotHold() throws Exception {
    stubIamUserInfo(OWNER_ID, Role.COMPANY);

    assertThat(getTimeToFill(UNKNOWN_JOB_ID).statusCode(), is(404));
  }

  private static JobPostEntity jobPost(String id, String source, Set<String> tags,
      Integer daysOpen, String closeReason) {
    return JobPostEntity.builder()
        .id(id)
        .title(id)
        .workType("REMOTE")
        .source(source)
        .status(daysOpen == null ? JobStatus.OPEN : JobStatus.CLOSE)
        .publishedOn(PUBLISHED_ON)
        .closedOn(daysOpen == null ? null : PUBLISHED_ON.plusDays(daysOpen))
        .closeReason(closeReason)
        .tags(new HashSet<>(tags))
        .detailsOccurredOn(OCCURRED_ON)
        .statusOccurredOn(OCCURRED_ON)
        .build();
  }

  private HttpResponse<String> getTimeToFill(String jobId) throws Exception {
    URI uri = URI.create("http://localhost:" + port + CONTEXT_PATH + "/dmz/analytics/job/"
        + jobId + "/time-to-fill");
    return httpClient.send(HttpRequest.newBuilder(uri)
            .header("Authorization", "Bearer test-access-token")
            .header("lynq-request-uuid", REQUEST_UUID)
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

  private void stubIamUserInfo(String userId, String role) {
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
                }""".formatted(userId, Role.PREFIX + role)));
  }
}
