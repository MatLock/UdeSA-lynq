package com.lynq.backend;

import com.lynq.backend.controller.request.SkillEnhanceRequest;
import com.lynq.backend.enums.UserType;
import com.lynq.backend.enums.WorkType;
import com.lynq.backend.model.CompanyEntity;
import com.lynq.backend.model.UserEntity;
import com.lynq.backend.repository.CompanyRepository;
import com.lynq.backend.repository.JobPostRepository;
import com.lynq.backend.repository.JobPostSkillRepository;
import com.lynq.backend.repository.UserRepository;
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
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

class LynqMLProxyE2ETest extends AbstractE2ETest {

  private static final String CONTEXT_PATH = "/lynq-app-backend";
  private static final String SKILL_ENHANCE_PROXY_PATH = "/ml/skill-enhance";
  private static final String ML_SKILL_ENHANCE_PATH = "/skill-enhance";
  private static final String VALIDATE_PATH = "/auth/validate";
  private static final String USERINFO_PATH = "/auth/user-info";

  private static final String AUTHORIZATION_HEADER = "Authorization";
  private static final String REQUEST_UUID_HEADER = "lynq-request-uuid";
  private static final String USER_ID_HEADER = "user-id";
  private static final String COMPANY_ID_HEADER = "company-id";
  private static final String CONTENT_TYPE_HEADER = "Content-Type";
  private static final String APPLICATION_JSON = "application/json";
  private static final String BEARER_TOKEN = "Bearer test-access-token";
  private static final String REQUEST_UUID = "550e8400-e29b-41d4-a716-446655440000";

  private static final String USER_ID = "11111111-1111-1111-1111-111111111111";
  private static final String USERNAME = "janedoe";
  private static final String EMAIL = "jane@lynq.com";
  private static final String COMPANY_ID = "22222222-2222-2222-2222-222222222222";
  private static final String COMPANY_NAME = "Lynq Technologies";

  private static final String TITLE = "Senior Backend Engineer";
  private static final String DESCRIPTION = "Build and scale the Lynq hiring platform.";
  private static final WorkType WORK_TYPE = WorkType.REMOTE;
  private static final String SKILL_JAVA = "Java";
  private static final String SKILL_SPRING = "Spring";

  @LocalServerPort
  private int port;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private CompanyRepository companyRepository;

  @Autowired
  private JobPostRepository jobPostRepository;

  @Autowired
  private JobPostSkillRepository jobPostSkillRepository;

  private final HttpClient httpClient = HttpClient.newHttpClient();

  @BeforeEach
  void setUp() {
    lynqIamMock.reset();
    lynqMlMock.reset();
    jobPostSkillRepository.deleteAll();
    jobPostRepository.deleteAll();
    companyRepository.deleteAll();
    userRepository.deleteAll();
  }

  @Test
  void enhanceSkillsAuthenticatesProxiesToMlWithHeadersAndReturnsSkills() throws Exception {
    stubIamValidateToken();
    stubIamUserInfo();
    seedCompanyOwnerWithCompany();
    stubMlSkillEnhance();

    HttpResponse<String> response = postSkillEnhance();

    assertThat(response.statusCode(), is(200));
    Map<String, Object> body = parse(response.body());
    assertThat(body.get("success"), is(true));

    @SuppressWarnings("unchecked")
    Map<String, Object> data = (Map<String, Object>) body.get("data");
    @SuppressWarnings("unchecked")
    List<String> skills = (List<String>) data.get("skills");
    assertThat(skills, contains(SKILL_JAVA, SKILL_SPRING));

    lynqMlMock.verify(request()
        .withMethod("POST")
        .withPath(ML_SKILL_ENHANCE_PATH)
        .withHeader(REQUEST_UUID_HEADER, REQUEST_UUID)
        .withHeader(USER_ID_HEADER, USER_ID)
        .withHeader(COMPANY_ID_HEADER, COMPANY_ID));
  }

  @Test
  void enhanceSkillsReturnsUnauthorizedWhenIamRejectsTokenAndDoesNotCallMl() throws Exception {
    stubIamInvalidToken();
    seedCompanyOwnerWithCompany();
    stubMlSkillEnhance();

    HttpResponse<String> response = postSkillEnhance();

    assertThat(response.statusCode(), is(401));
    lynqMlMock.verify(request().withPath(ML_SKILL_ENHANCE_PATH), VerificationTimes.exactly(0));
  }

  @Test
  void enhanceSkillsReturnsForbiddenWhenRequestUuidHeaderMissing() throws Exception {
    stubIamValidateToken();
    stubIamUserInfo();
    seedCompanyOwnerWithCompany();
    stubMlSkillEnhance();

    HttpRequest httpRequest = HttpRequest.newBuilder()
        .uri(URI.create(skillEnhanceUrl()))
        .header(CONTENT_TYPE_HEADER, APPLICATION_JSON)
        .header(AUTHORIZATION_HEADER, BEARER_TOKEN)
        .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(validRequest())))
        .build();
    HttpResponse<String> response =
        httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

    assertThat(response.statusCode(), is(403));
    lynqMlMock.verify(request().withPath(ML_SKILL_ENHANCE_PATH), VerificationTimes.exactly(0));
  }

  @Test
  void enhanceSkillsReturnsBadRequestWhenUserIsNotCompanyType() throws Exception {
    stubIamValidateToken();
    stubIamUserInfo();
    userRepository.save(UserEntity.builder()
        .id(USER_ID)
        .type(UserType.CANDIDATE)
        .createdOn(LocalDate.now())
        .build());
    stubMlSkillEnhance();

    HttpResponse<String> response = postSkillEnhance();

    assertThat(response.statusCode(), is(400));
    assertThat(parse(response.body()).get("success"), is(false));
    lynqMlMock.verify(request().withPath(ML_SKILL_ENHANCE_PATH), VerificationTimes.exactly(0));
  }

  @Test
  void enhanceSkillsReturnsBadRequestWhenCompanyOwnerHasNoCompany() throws Exception {
    stubIamValidateToken();
    stubIamUserInfo();
    userRepository.save(UserEntity.builder()
        .id(USER_ID)
        .type(UserType.COMPANY)
        .createdOn(LocalDate.now())
        .build());
    stubMlSkillEnhance();

    HttpResponse<String> response = postSkillEnhance();

    assertThat(response.statusCode(), is(400));
    assertThat(parse(response.body()).get("success"), is(false));
    lynqMlMock.verify(request().withPath(ML_SKILL_ENHANCE_PATH), VerificationTimes.exactly(0));
  }

  private void seedCompanyOwnerWithCompany() {
    UserEntity owner = userRepository.save(UserEntity.builder()
        .id(USER_ID)
        .type(UserType.COMPANY)
        .createdOn(LocalDate.now())
        .build());
    companyRepository.save(CompanyEntity.builder()
        .id(COMPANY_ID)
        .name(COMPANY_NAME)
        .createdOn(LocalDate.now())
        .owner(owner)
        .build());
  }

  private HttpResponse<String> postSkillEnhance() throws Exception {
    HttpRequest httpRequest = HttpRequest.newBuilder()
        .uri(URI.create(skillEnhanceUrl()))
        .header(CONTENT_TYPE_HEADER, APPLICATION_JSON)
        .header(AUTHORIZATION_HEADER, BEARER_TOKEN)
        .header(REQUEST_UUID_HEADER, REQUEST_UUID)
        .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(validRequest())))
        .build();
    return httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
  }

  private SkillEnhanceRequest validRequest() {
    SkillEnhanceRequest request = new SkillEnhanceRequest();
    request.setTitle(TITLE);
    request.setDescription(DESCRIPTION);
    request.setWorkType(WORK_TYPE);
    return request;
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> parse(String json) {
    return objectMapper.readValue(json, Map.class);
  }

  private void stubMlSkillEnhance() {
    lynqMlMock.when(request().withMethod("POST").withPath(ML_SKILL_ENHANCE_PATH))
        .respond(response()
            .withStatusCode(200)
            .withContentType(MediaType.APPLICATION_JSON)
            .withBody("""
                {"success": true, "data": {"skills": ["%s", "%s"]}}"""
                .formatted(SKILL_JAVA, SKILL_SPRING)));
  }

  private void stubIamValidateToken() {
    lynqIamMock.when(request().withMethod("GET").withPath(VALIDATE_PATH))
        .respond(response()
            .withStatusCode(200)
            .withContentType(MediaType.APPLICATION_JSON)
            .withBody("""
                {"success": true, "data": true}"""));
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
                    "email": "%s"
                  }
                }""".formatted(USER_ID, USERNAME, EMAIL)));
  }

  private void stubIamInvalidToken() {
    lynqIamMock.when(request().withMethod("GET").withPath(VALIDATE_PATH))
        .respond(response()
            .withStatusCode(200)
            .withContentType(MediaType.APPLICATION_JSON)
            .withBody("""
                {"success": true, "data": false}"""));
  }

  private String skillEnhanceUrl() {
    return "http://localhost:" + port + CONTEXT_PATH + SKILL_ENHANCE_PROXY_PATH;
  }
}
