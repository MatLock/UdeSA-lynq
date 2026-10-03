package com.lynq.analytics;

import com.lynq.analytics.enums.JobStatus;
import com.lynq.analytics.model.CandidateEntity;
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

class SalaryApplicationTests extends AbstractE2ETest {

  private static final String CONTEXT_PATH = "/lynq-analytics";
  private static final String USERINFO_PATH = "/auth/user-info";
  private static final String REQUEST_UUID = "550e8400-e29b-41d4-a716-446655440000";
  private static final String USER_ID = "11111111-1111-1111-1111-111111111111";
  private static final String JOB_ID = "REF";
  private static final String UNKNOWN_JOB_ID = "88888888-8888-8888-8888-888888888888";
  private static final Set<String> TAGS = Set.of("backend", "sql", "cloud");
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
    jobPostRepository.save(jobPost(JOB_ID, 1000, 2000, "ARS"));
    jobPostRepository.save(jobPost("S1", 100, 200, "ARS"));
    jobPostRepository.save(jobPost("S2", 200, null, "ARS"));
    jobPostRepository.save(jobPost("S3", null, 300, "ARS"));
    jobPostRepository.save(jobPost("S4", 400, 400, "ARS"));
    jobPostRepository.save(jobPost("S5", 500, 700, "ARS"));
    jobPostRepository.save(jobPost("DOLLARS", 3000, 4000, "USD"));
    jobPostRepository.save(jobPost("NO_SALARY", null, null, null));
    candidateRepository.save(candidate("C1", 1000, "ARS"));
    candidateRepository.save(candidate("C2", 2000, "ARS"));
    candidateRepository.save(candidate("C_USD", 3000, "USD"));
    candidateRepository.save(candidate("C_NONE", null, null));
    stubIamUserInfo(Role.CANDIDATE);
  }

  @Test
  void answersTheMedianOfSimilarPositionsAndWithholdsAShortSampleOfPeers() throws Exception {
    HttpResponse<String> response = getSalary(JOB_ID);

    assertThat(response.statusCode(), is(200));
    Map<String, Object> position = block(response, "positionSalary");
    assertThat(position.get("median"), is(300.0));
    assertThat(position.get("p25"), is(200.0));
    assertThat(position.get("p75"), is(400.0));
    assertThat(position.get("n"), is(5));
    assertThat(position.get("currency"), is("ARS"));
    assertThat(position.get("insufficientData"), is(false));
    Map<String, Object> peers = block(response, "peersExpectedSalary");
    assertThat(peers.get("median"), is(nullValue()));
    assertThat(peers.get("n"), is(2));
    assertThat(peers.get("currency"), is("ARS"));
    assertThat(peers.get("insufficientData"), is(true));
  }

  @Test
  void comparesAJobPostInDollarsOnlyWithSalariesInDollars() throws Exception {
    Map<String, Object> position = block(getSalary("DOLLARS"), "positionSalary");

    assertThat(position.get("currency"), is("USD"));
    assertThat(position.get("n"), is(0));
    assertThat(position.get("insufficientData"), is(true));
  }

  @Test
  void answersACompanyToo() throws Exception {
    lynqIamMock.reset();
    stubIamUserInfo(Role.COMPANY);

    assertThat(getSalary(JOB_ID).statusCode(), is(200));
  }

  @Test
  void servesTheCachedAnswerUntilItExpires() throws Exception {
    getSalary(JOB_ID);
    jobPostRepository.save(jobPost("S6", 9000, 9000, "ARS"));

    Map<String, Object> position = block(getSalary(JOB_ID), "positionSalary");

    assertThat(redisTemplate.hasKey("lynq-analytics::salary::" + JOB_ID), is(true));
    assertThat(position.get("n"), is(5));
  }

  @Test
  void answersNotFoundForAJobPostAnalyticsDoesNotHold() throws Exception {
    HttpResponse<String> response = getSalary(UNKNOWN_JOB_ID);

    assertThat(response.statusCode(), is(404));
    assertThat(parse(response.body()).get("reason"),
        is("Job post '" + UNKNOWN_JOB_ID + "' not found"));
  }

  private static JobPostEntity jobPost(String id, Integer down, Integer top, String currency) {
    return JobPostEntity.builder()
        .id(id)
        .title(id)
        .workType("REMOTE")
        .source("LYNQ")
        .status(JobStatus.OPEN)
        .publishedOn(LocalDate.parse("2026-09-20"))
        .salaryRangeDown(down)
        .salaryRangeTop(top)
        .salaryCurrency(currency)
        .tags(new HashSet<>(TAGS))
        .detailsOccurredOn(OCCURRED_ON)
        .statusOccurredOn(OCCURRED_ON)
        .build();
  }

  private static CandidateEntity candidate(String id, Integer expectedSalary, String currency) {
    return CandidateEntity.builder()
        .id(id)
        .expectedSalary(expectedSalary)
        .expectedSalaryCurrency(currency)
        .tags(new HashSet<>(TAGS))
        .skillsOccurredOn(OCCURRED_ON)
        .salaryOccurredOn(OCCURRED_ON)
        .build();
  }

  private HttpResponse<String> getSalary(String jobId) throws Exception {
    URI uri = URI.create("http://localhost:" + port + CONTEXT_PATH + "/dmz/analytics/job/"
        + jobId + "/salary");
    return httpClient.send(HttpRequest.newBuilder(uri)
            .header("Authorization", "Bearer test-access-token")
            .header("lynq-request-uuid", REQUEST_UUID)
            .GET()
            .build(),
        HttpResponse.BodyHandlers.ofString());
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> block(HttpResponse<String> response, String name) {
    Map<String, Object> data = (Map<String, Object>) parse(response.body()).get("data");
    return (Map<String, Object>) data.get(name);
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
