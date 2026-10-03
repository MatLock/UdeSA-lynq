package com.lynq.analytics;

import com.lynq.analytics.enums.JobStatus;
import com.lynq.analytics.model.ApplicationEntity;
import com.lynq.analytics.model.CandidateEntity;
import com.lynq.analytics.model.JobPostEntity;
import com.lynq.analytics.repository.ApplicationRepository;
import com.lynq.analytics.repository.CandidateDailyBenchmarkRepository;
import com.lynq.analytics.repository.CandidateRepository;
import com.lynq.analytics.repository.CategoryDailySalaryRepository;
import com.lynq.analytics.repository.JobDailyStatsRepository;
import com.lynq.analytics.repository.JobPostRepository;
import com.lynq.analytics.repository.SkillDailyDemandRepository;
import com.lynq.analytics.repository.TagFrequencyRepository;
import com.lynq.analytics.security.Role;
import com.lynq.analytics.service.DailySnapshotService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockserver.model.MediaType;
import org.mockserver.verify.VerificationTimes;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

class MarketFitApplicationTests extends AbstractE2ETest {

  private static final String CONTEXT_PATH = "/lynq-analytics";
  private static final String USERINFO_PATH = "/auth/user-info";
  private static final String SCORE_BATCH_PATH = "/internal/score/batch";
  private static final String INTERNAL_TOKEN = "test-internal-token";
  private static final String REQUEST_UUID = "550e8400-e29b-41d4-a716-446655440000";
  private static final String USER_ID = "11111111-1111-1111-1111-111111111111";
  private static final Set<String> BACKEND_TAGS = Set.of("backend", "java ecosystem");
  private static final Instant OCCURRED_ON = Instant.parse("2026-09-20T10:00:00Z");
  private static final LocalDate PUBLISHED_ON =
      LocalDate.now(ZoneId.of("America/Argentina/Buenos_Aires")).minusDays(10);
  private static final Duration TIMEOUT = Duration.ofSeconds(30);

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
  private CandidateDailyBenchmarkRepository candidateDailyBenchmarkRepository;

  @Autowired
  private JobDailyStatsRepository jobDailyStatsRepository;

  @Autowired
  private SkillDailyDemandRepository skillDailyDemandRepository;

  @Autowired
  private CategoryDailySalaryRepository categoryDailySalaryRepository;

  @Autowired
  private DailySnapshotService dailySnapshotService;

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
    candidateDailyBenchmarkRepository.deleteAll();
    jobDailyStatsRepository.deleteAll();
    skillDailyDemandRepository.deleteAll();
    categoryDailySalaryRepository.deleteAll();
    applicationRepository.deleteAll();
    jobPostRepository.deleteAll();
    candidateRepository.deleteAll();
    tagFrequencyRepository.deleteAll();
    givenTheMarket();
  }

  @Test
  void refusesTheSnapshotTriggerWithoutTheInternalToken() throws Exception {
    HttpResponse<String> response = send(HttpRequest
        .newBuilder(uri("/internal/snapshot"))
        .header("lynq-request-uuid", REQUEST_UUID)
        .header("Authorization", "Bearer test-access-token")
        .POST(HttpRequest.BodyPublishers.noBody()));

    assertThat(response.statusCode(), is(401));
    assertThat(dailySnapshotService.isRunning(), is(false));
  }

  @Test
  void takesTheSnapshotAndServesTheCallersPositionAmongTheirPeers() throws Exception {
    givenTheBackendScores();
    stubIamUserInfo(Role.CANDIDATE);

    takeTheSnapshot();

    Map<String, Object> benchmark = data(get("/dmz/analytics/candidate/me/benchmark"));
    assertThat(benchmark.get("snapshotOn"), is(today().toString()));
    assertThat(benchmark.get("jobsScored"), is(6));
    assertThat(benchmark.get("marketFit"), is(50));
    assertThat(benchmark.get("aboveThresholdPct"), is(0));
    assertThat(benchmark.get("reachThreshold"), is(60));
    assertThat(benchmark.get("peerGroupSize"), is(5));
    assertThat(benchmark.get("peerPercentile"), is(8));
    assertThat(benchmark.get("peerFitMedian"), is(100));
    assertThat(benchmark.get("skillCoveragePct"), is(50));
    assertThat(benchmark.get("skillUnlocks"),
        is(List.of(Map.of("skill", "Kafka", "jobsUnlocked", 6))));
    assertThat(((List<?>) benchmark.get("series")).size(), is(1));
    lynqIamMock.verify(request().withMethod("POST").withPath(SCORE_BATCH_PATH)
        .withHeader("lynq-internal-token", INTERNAL_TOKEN), VerificationTimes.exactly(2));
  }

  @Test
  void takesTheSnapshotOfTheMarket() throws Exception {
    givenTheBackendScores();
    stubIamUserInfo(Role.COMPANY);

    takeTheSnapshot();

    Map<String, Object> market = data(get("/dmz/analytics/market"));
    assertThat(market.get("snapshotOn"), is(today().toString()));
    assertThat(market.get("openJobPosts"), is(8));
    assertThat(market.get("openWithSalary"), is(6));
    List<Map<String, Object>> skills = list(market.get("skillDemand"));
    assertThat(skills.stream().map(skill -> skill.get("skill")).toList(),
        contains("Java", "Kafka", "Figma"));
    assertThat(skills.getFirst().get("openJobPosts"), is(6));
    assertThat(skills.getFirst().get("weeklyChange"), is(nullValue()));
    List<Map<String, Object>> salaries = list(((Map<?, ?>) market.get("salary")).get("rows"));
    assertThat(salaries.size(), is(1));
    assertThat(salaries.getFirst().get("category"), is("TECNOLOGIA"));
    assertThat(salaries.getFirst().get("median"), is(350.0));
    assertThat(list(market.get("publishedPerWeek")).stream()
        .mapToInt(week -> (Integer) week.get("jobPosts")).sum(), is(8));
  }

  @Test
  void keepsTheMarketAndLeavesTheBenchmarkAsAHoleWhenTheBackendIsDown() throws Exception {
    lynqIamMock.when(request().withMethod("POST").withPath(SCORE_BATCH_PATH))
        .respond(response().withStatusCode(503));
    stubIamUserInfo(Role.CANDIDATE);

    takeTheSnapshot();

    assertThat(jobDailyStatsRepository.findBySnapshotOn(today()).isEmpty(), is(false));
    assertThat(candidateDailyBenchmarkRepository.count(), is(0L));
    Map<String, Object> benchmark = data(get("/dmz/analytics/candidate/me/benchmark"));
    assertThat(benchmark.get("snapshotOn"), is(nullValue()));
    assertThat(list(benchmark.get("series")), is(empty()));
  }

  @Test
  void comparesTheJobPostsOfTheAuthenticatedCompany() throws Exception {
    stubIamUserInfo(Role.COMPANY);
    List<Integer> scores = List.of(40, 50, 60, 70, 80);
    for (int i = 0; i < scores.size(); i++) {
      applicationRepository.save(application("b1", "applicant-" + i, scores.get(i)));
    }
    applicationRepository.save(application("b2", "applicant-0", 90));

    List<Map<String, Object>> jobs =
        list(data(get("/dmz/analytics/company/me/jobs")).get("jobs"));

    assertThat(jobs.stream().map(job -> job.get("jobId")).toList(), contains("b1", "b2"));
    assertThat(jobs.get(0).get("applications"), is(5));
    assertThat(jobs.get(0).get("medianScore"), is(60.0));
    assertThat(jobs.get(1).get("applications"), is(1));
    assertThat(jobs.get(1).get("medianScore"), is(nullValue()));
    assertThat(jobs.get(1).get("insufficientData"), is(true));
  }

  @Test
  void refusesTheCompanyJobPostsToACandidate() throws Exception {
    stubIamUserInfo(Role.CANDIDATE);

    HttpResponse<String> response = get("/dmz/analytics/company/me/jobs");

    assertThat(response.statusCode(), is(403));
  }

  private void givenTheMarket() {
    for (int i = 1; i <= 6; i++) {
      jobPostRepository.save(jobPost("b" + i, "TECNOLOGIA", "REMOTE", i * 100,
          Set.of("Java", "Kafka"), BACKEND_TAGS, i <= 2 ? USER_ID : null));
    }
    jobPostRepository.save(jobPost("d1", null, "ONSITE", null, Set.of("Figma"),
        Set.of("design"), null));
    jobPostRepository.save(jobPost("d2", null, "ONSITE", null, Set.of("Figma"),
        Set.of("design"), null));
    jobPostRepository.save(closedJobPost());

    candidateRepository.save(candidate(USER_ID, Set.of("Java"), BACKEND_TAGS));
    for (int i = 1; i <= 5; i++) {
      candidateRepository.save(candidate("peer-" + i, Set.of("Java", "Kafka"), BACKEND_TAGS));
    }
    candidateRepository.save(candidate("designer", Set.of("Figma"), Set.of("design")));
  }

  @SuppressWarnings("unchecked")
  private void givenTheBackendScores() {
    lynqIamMock.when(request().withMethod("POST").withPath(SCORE_BATCH_PATH)
            .withHeader("lynq-internal-token", INTERNAL_TOKEN))
        .respond(httpRequest -> {
          Map<String, Object> batch = objectMapper.readValue(httpRequest.getBodyAsString(),
              Map.class);
          Map<String, Set<String>> jobSkills = skillsById(batch.get("jobPosts"));
          Map<String, Set<String>> candidateSkills = skillsById(batch.get("candidates"));
          List<Map<String, Object>> scores = new ArrayList<>();
          for (Map<String, Object> pair : list(batch.get("pairs"))) {
            String jobId = (String) pair.get("jobId");
            String candidateId = (String) pair.get("candidateId");
            Set<String> asked = jobSkills.get(jobId);
            long covered = asked.stream().filter(candidateSkills.get(candidateId)::contains)
                .count();
            scores.add(Map.of("jobId", jobId, "candidateId", candidateId,
                "score", (int) Math.round(100.0 * covered / asked.size())));
          }
          return response()
              .withStatusCode(200)
              .withContentType(MediaType.APPLICATION_JSON)
              .withBody(objectMapper.writeValueAsString(
                  Map.of("success", true, "data", Map.of("scores", scores))));
        });
  }

  private Map<String, Set<String>> skillsById(Object profiles) {
    return list(profiles).stream().collect(Collectors.toMap(
        profile -> (String) profile.get("id"),
        profile -> ((List<?>) profile.get("skills")).stream()
            .map(skill -> ((String) skill).toLowerCase(Locale.ROOT))
            .collect(Collectors.toSet())));
  }

  private void takeTheSnapshot() throws Exception {
    HttpResponse<String> response = send(HttpRequest
        .newBuilder(uri("/internal/snapshot"))
        .header("lynq-request-uuid", REQUEST_UUID)
        .header("lynq-internal-token", INTERNAL_TOKEN)
        .POST(HttpRequest.BodyPublishers.noBody()));

    assertThat(response.statusCode(), is(202));
    await().atMost(TIMEOUT).until(() -> !dailySnapshotService.isRunning());
  }

  private HttpResponse<String> get(String path) throws Exception {
    return send(HttpRequest.newBuilder(uri(path))
        .header("Authorization", "Bearer test-access-token")
        .header("lynq-request-uuid", REQUEST_UUID)
        .GET());
  }

  private HttpResponse<String> send(HttpRequest.Builder request)
      throws Exception {
    return httpClient.send(request.build(), HttpResponse.BodyHandlers.ofString());
  }

  private URI uri(String path) {
    return URI.create("http://localhost:" + port + CONTEXT_PATH + path);
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> data(HttpResponse<String> response) {
    assertThat(response.body(), response.statusCode(), is(200));
    return (Map<String, Object>) objectMapper.readValue(response.body(), Map.class).get("data");
  }

  @SuppressWarnings("unchecked")
  private static List<Map<String, Object>> list(Object value) {
    return (List<Map<String, Object>>) value;
  }

  private static LocalDate today() {
    return LocalDate.now(ZoneId.of("America/Argentina/Buenos_Aires"));
  }

  private static JobPostEntity jobPost(String id, String category, String workType,
      Integer salary, Set<String> skills, Set<String> tags, String createdByUserId) {
    return JobPostEntity.builder()
        .id(id)
        .title("Title " + id)
        .category(category)
        .workType(workType)
        .source(createdByUserId != null ? "LYNQ" : "COMPUTRABAJO")
        .createdByUserId(createdByUserId)
        .status(JobStatus.OPEN)
        .publishedOn(createdByUserId != null && id.equals("b2")
            ? PUBLISHED_ON.minusDays(1) : PUBLISHED_ON)
        .salaryRangeDown(salary)
        .salaryRangeTop(salary)
        .salaryCurrency(salary != null ? "ARS" : null)
        .skills(new HashSet<>(skills))
        .tags(new HashSet<>(tags))
        .detailsOccurredOn(OCCURRED_ON)
        .statusOccurredOn(OCCURRED_ON)
        .build();
  }

  private static JobPostEntity closedJobPost() {
    JobPostEntity closed = jobPost("closed", "TECNOLOGIA", "REMOTE", 9000, Set.of("Cobol"),
        BACKEND_TAGS, null);
    closed.setStatus(JobStatus.CLOSE);
    closed.setPublishedOn(LocalDate.parse("2020-01-06"));
    closed.setClosedOn(LocalDate.parse("2020-02-01"));
    return closed;
  }

  private static CandidateEntity candidate(String id, Set<String> skills, Set<String> tags) {
    return CandidateEntity.builder()
        .id(id)
        .skills(new HashSet<>(skills))
        .tags(new HashSet<>(tags))
        .skillsOccurredOn(OCCURRED_ON)
        .build();
  }

  private static ApplicationEntity application(String jobId, String candidateId, int score) {
    return ApplicationEntity.builder()
        .id(UUID.nameUUIDFromBytes((jobId + candidateId).getBytes()).toString())
        .jobId(jobId)
        .candidateId(candidateId)
        .appliedOn(PUBLISHED_ON)
        .lynqScore(score)
        .occurredOn(OCCURRED_ON)
        .build();
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
