package com.lynq.backend;

import com.lynq.backend.enums.CloseReason;
import com.lynq.backend.enums.JobPostSource;
import com.lynq.backend.enums.JobStatus;
import com.lynq.backend.enums.WorkType;
import com.lynq.backend.model.JobPostEntity;
import com.lynq.backend.repository.JobPostRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.TestPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

@TestPropertySource(properties = {
    "lynq.internal.token=" + JobVerificationApplicationTests.INTERNAL_TOKEN,
    "lynq.verification.window-days=20",
    "lynq.verification.quota-per-category=2",
    "lynq.verification.expire-after-days=25"
})
class JobVerificationApplicationTests extends AbstractE2ETest {

  static final String INTERNAL_TOKEN = "test-internal-token";

  private static final String CONTEXT_PATH = "/lynq-app-backend";
  private static final String CANDIDATES_PATH = "/internal/job-posts/verification-candidates";
  private static final String LIVENESS_PATH = "/internal/job-posts/liveness";
  private static final String EXPIRE_PATH = "/internal/job-posts/expire";
  private static final String INTERNAL_TOKEN_HEADER = "lynq-internal-token";
  private static final String REQUEST_UUID_HEADER = "lynq-request-uuid";
  private static final String REQUEST_UUID = "550e8400-e29b-41d4-a716-446655440000";
  private static final String TECNOLOGIA = "TECNOLOGIA";
  private static final String ADMINISTRACION = "ADMINISTRACION";

  private static final LocalDate TODAY = LocalDate.now(ZoneOffset.UTC);
  private static final LocalDate STALE = TODAY.minusDays(30);
  private static final LocalDate FRESH = TODAY.minusDays(3);

  @LocalServerPort
  private int port;

  @Autowired
  private JobPostRepository jobPostRepository;

  @Autowired
  private ObjectMapper objectMapper;

  private final HttpClient httpClient = HttpClient.newHttpClient();

  @BeforeEach
  void setUp() {
    jobPostRepository.deleteAll();
  }

  @Test
  void candidatesAreTheStaleOpenExternalsCappedPerCategoryNeverCheckedFirstAndInterleaved()
      throws Exception {
    save(external("tec-never", TECNOLOGIA).build());
    save(external("tec-checked-recently", TECNOLOGIA).lastCheckedOn(TODAY.minusDays(5)).build());
    save(external("tec-checked-long-ago", TECNOLOGIA).lastCheckedOn(TODAY.minusDays(10)).build());
    save(external("adm-never", ADMINISTRACION).build());
    save(external("fresh", TECNOLOGIA).lastSeenOn(FRESH).build());
    save(external("checked-today", ADMINISTRACION).lastCheckedOn(TODAY).build());
    save(external("closed", ADMINISTRACION).jobStatus(JobStatus.CLOSE).closedOn(FRESH).build());
    save(external("no-url", ADMINISTRACION).jobUrl(null).build());
    save(external("lynq", ADMINISTRACION).jobPostSource(JobPostSource.LYNQ).build());

    HttpResponse<String> response = send("GET", CANDIDATES_PATH, null, INTERNAL_TOKEN);

    assertThat(response.statusCode(), is(200));
    JsonNode candidates = objectMapper.readTree(response.body()).path("data").path("candidates");
    List<String> ids = new ArrayList<>();
    candidates.forEach(candidate -> ids.add(candidate.path("id").asText()));
    assertThat(ids, contains("adm-never", "tec-never", "tec-checked-long-ago"));
    JsonNode first = candidates.get(0);
    assertThat(first.path("jobUrl").asText(), is("https://www.bumeran.com.ar/empleos/adm-never.html"));
    assertThat(first.path("source").asText(), is("BUMERAN"));
    assertThat(first.path("category").asText(), is(ADMINISTRACION));
  }

  @Test
  void livenessAppliesEveryOutcomeAndSkipsWhatIsNotAnOpenExternal() throws Exception {
    save(external("alive", TECNOLOGIA).build());
    save(external("finished", TECNOLOGIA).build());
    save(external("gone", TECNOLOGIA).build());
    save(external("unknown", TECNOLOGIA).build());
    save(external("lynq", TECNOLOGIA).jobPostSource(JobPostSource.LYNQ).build());
    String body = """
        {"reports": [
          {"id": "alive", "outcome": "ALIVE"},
          {"id": "finished", "outcome": "CLOSED"},
          {"id": "gone", "outcome": "GONE"},
          {"id": "unknown", "outcome": "UNKNOWN"},
          {"id": "lynq", "outcome": "GONE"},
          {"id": "missing", "outcome": "ALIVE"}
        ]}""";

    HttpResponse<String> response = send("POST", LIVENESS_PATH, body, INTERNAL_TOKEN);

    assertThat(response.statusCode(), is(200));
    JsonNode data = objectMapper.readTree(response.body()).path("data");
    assertThat(data.path("alive").asInt(), is(1));
    assertThat(data.path("closed").asInt(), is(1));
    assertThat(data.path("gone").asInt(), is(1));
    assertThat(data.path("unknown").asInt(), is(1));
    assertThat(data.path("skipped").asInt(), is(2));

    JobPostEntity alive = jobPostRepository.findById("alive").orElseThrow();
    assertThat(alive.getJobStatus(), is(JobStatus.OPEN));
    assertThat(alive.getLastSeenOn(), is(TODAY));
    assertThat(alive.getLastCheckedOn(), is(TODAY));

    JobPostEntity finished = jobPostRepository.findById("finished").orElseThrow();
    assertThat(finished.getJobStatus(), is(JobStatus.CLOSE));
    assertThat(finished.getClosedOn(), is(TODAY));
    assertThat(finished.getCloseReason(), is(CloseReason.VERIFIED_CLOSED));

    JobPostEntity gone = jobPostRepository.findById("gone").orElseThrow();
    assertThat(gone.getCloseReason(), is(CloseReason.VERIFIED_GONE));

    JobPostEntity unknown = jobPostRepository.findById("unknown").orElseThrow();
    assertThat(unknown.getJobStatus(), is(JobStatus.OPEN));
    assertThat(unknown.getLastSeenOn(), is(STALE));
    assertThat(unknown.getLastCheckedOn(), is(TODAY));

    assertThat(jobPostRepository.findById("lynq").orElseThrow().getCloseReason(), is(nullValue()));
  }

  @Test
  void livenessRejectsAnUnknownOutcome() throws Exception {
    save(external("alive", TECNOLOGIA).build());

    HttpResponse<String> response = send("POST", LIVENESS_PATH,
        "{\"reports\": [{\"id\": \"alive\", \"outcome\": \"MAYBE\"}]}", INTERNAL_TOKEN);

    assertThat(response.statusCode(), is(400));
  }

  @Test
  void expireClosesTheOpenExternalsNotSeenForTooLongWithOrWithoutAUrl() throws Exception {
    save(external("stale", TECNOLOGIA).build());
    save(external("stale-no-url", TECNOLOGIA).jobUrl(null).build());
    save(external("fresh", TECNOLOGIA).lastSeenOn(FRESH).build());
    save(external("lynq", TECNOLOGIA).jobPostSource(JobPostSource.LYNQ).build());

    HttpResponse<String> response = send("POST", EXPIRE_PATH, null, INTERNAL_TOKEN);

    assertThat(response.statusCode(), is(200));
    assertThat(objectMapper.readTree(response.body()).path("data").path("expired").asInt(), is(2));
    JobPostEntity stale = jobPostRepository.findById("stale-no-url").orElseThrow();
    assertThat(stale.getJobStatus(), is(JobStatus.CLOSE));
    assertThat(stale.getClosedOn(), is(TODAY));
    assertThat(stale.getCloseReason(), is(CloseReason.EXPIRED_BY_POLICY));
    assertThat(jobPostRepository.findById("fresh").orElseThrow().getJobStatus(),
        is(JobStatus.OPEN));
    assertThat(jobPostRepository.findById("lynq").orElseThrow().getJobStatus(),
        is(JobStatus.OPEN));
  }

  @Test
  void everyVerificationRouteRefusesACallWithoutTheInternalToken() throws Exception {
    assertThat(send("GET", CANDIDATES_PATH, null, null).statusCode(), is(401));
    assertThat(send("POST", LIVENESS_PATH,
        "{\"reports\": [{\"id\": \"x\", \"outcome\": \"ALIVE\"}]}", null).statusCode(), is(401));
    assertThat(send("POST", EXPIRE_PATH, null, "not-the-token").statusCode(), is(401));
  }

  private JobPostEntity.JobPostEntityBuilder external(String id, String category) {
    return JobPostEntity.builder()
        .id(id)
        .title("Posting " + id)
        .workType(WorkType.REMOTE)
        .jobPostSource(JobPostSource.BUMERAN)
        .jobStatus(JobStatus.OPEN)
        .jobUrl("https://www.bumeran.com.ar/empleos/" + id + ".html")
        .category(category)
        .createdOn(STALE)
        .lastSeenOn(STALE)
        .totalSeen(0L);
  }

  private void save(JobPostEntity job) {
    jobPostRepository.save(job);
  }

  private HttpResponse<String> send(String method, String path, String body, String token)
      throws Exception {
    HttpRequest.Builder builder = HttpRequest.newBuilder()
        .uri(URI.create("http://localhost:" + port + CONTEXT_PATH + path))
        .header(REQUEST_UUID_HEADER, REQUEST_UUID)
        .header("Content-Type", "application/json")
        .method(method, body == null
            ? HttpRequest.BodyPublishers.noBody()
            : HttpRequest.BodyPublishers.ofString(body));
    if (token != null) {
      builder.header(INTERNAL_TOKEN_HEADER, token);
    }
    return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
  }
}
