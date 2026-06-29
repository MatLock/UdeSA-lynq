package com.lynq.backend;

import com.lynq.backend.controller.request.CreateJobRequest;
import com.lynq.backend.controller.request.CreateUserRequest;
import com.lynq.backend.controller.request.CreateUserWithCompanyRequest;
import com.lynq.backend.controller.request.UpdateUserProfileRequest;
import com.lynq.backend.enums.JobPostSource;
import com.lynq.backend.enums.UserType;
import com.lynq.backend.enums.WorkType;
import com.lynq.backend.model.CompanyEntity;
import com.lynq.backend.model.JobPostEntity;
import com.lynq.backend.model.UserEntity;
import com.lynq.backend.repository.CompanyRepository;
import com.lynq.backend.repository.JobPostRepository;
import com.lynq.backend.repository.JobPostSkillRepository;
import com.lynq.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockserver.model.MediaType;
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
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

class BackendAppApplicationTests extends AbstractE2ETest {

  private static final String CONTEXT_PATH = "/lynq-app-backend";
  private static final String CREATE_USER_PATH = "/user";
  private static final String CREATE_COMPANY_PATH = "/company";
  private static final String CREATE_JOB_PATH = "/job";
  private static final String VALIDATE_PATH = "/auth/validate";
  private static final String USERINFO_PATH = "/auth/user-info";

  private static final String AUTHORIZATION_HEADER = "Authorization";
  private static final String REQUEST_UUID_HEADER = "lynq-request-uuid";
  private static final String CONTENT_TYPE_HEADER = "Content-Type";
  private static final String APPLICATION_JSON = "application/json";
  private static final String BEARER_TOKEN = "Bearer test-access-token";
  private static final String REQUEST_UUID = "550e8400-e29b-41d4-a716-446655440000";

  private static final String USER_ID = "11111111-1111-1111-1111-111111111111";
  private static final String USERNAME = "janedoe";
  private static final String EMAIL = "jane@lynq.com";

  private static final String CURRENT_POSITION = "Backend Engineer";
  private static final String FULL_NAME = "Jane Doe";
  private static final String UPDATED_FULL_NAME = "Jane Q. Doe";
  private static final String UPDATED_CURRENT_POSITION = "Staff Engineer";
  private static final String ABOUT = "Java developer focused on distributed systems.";
  private static final String PROFILE_IMAGE_URL = "https://cdn.lynq.com/avatars/jane.png";
  private static final String GITHUB_URL = "https://github.com/janedoe";
  private static final String LINKEDIN_URL = "https://linkedin.com/in/janedoe";
  private static final LocalDate BIRTH_DATE = LocalDate.of(1995, 4, 12);

  private static final String COMPANY_NAME = "Lynq Technologies";
  private static final String COMPANY_ABOUT = "We build talent matching platforms.";
  private static final Integer COMPANY_SIZE = 250;
  private static final String COMPANY_PROFILE_IMAGE_URL = "https://cdn.lynq.com/logos/lynq.png";
  private static final String COMPANY_ID = "22222222-2222-2222-2222-222222222222";

  private static final String JOB_TITLE = "Senior Backend Engineer";
  private static final String JOB_DESCRIPTION = "Build and scale the Lynq hiring platform.";
  private static final WorkType JOB_WORK_TYPE = WorkType.REMOTE;
  private static final Integer JOB_SALARY_RANGE_DOWN = 80000;
  private static final Integer JOB_SALARY_RANGE_TOP = 120000;
  private static final JobPostSource JOB_POST_TYPE = JobPostSource.LYNQ;
  private static final List<String> JOB_SKILLS = List.of("Java", "Spring", "PostgreSQL");

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
    jobPostSkillRepository.deleteAll();
    jobPostRepository.deleteAll();
    companyRepository.deleteAll();
    userRepository.deleteAll();
  }

  @Test
  void createUserAuthenticatesAgainstIamPersistsUserAndReturnsCreated() throws Exception {
    stubIamValidateToken();
    stubIamUserInfo();

    HttpResponse<String> response = postCreateUser();

    assertThat(response.statusCode(), is(201));
    Map<String, Object> body = parse(response.body());
    assertThat(body.get("success"), is(true));

    @SuppressWarnings("unchecked")
    Map<String, Object> data = (Map<String, Object>) body.get("data");
    assertThat(data.get("id"), is(USER_ID));
    assertThat(data.get("userType"), is(UserType.CANDIDATE.name()));
    assertThat(data.get("fullName"), is(FULL_NAME));
    assertThat(data.get("currentPosition"), is(CURRENT_POSITION));
    assertThat(data.get("about"), is(ABOUT));
    assertThat(data.get("birthDate"), is(BIRTH_DATE.toString()));
    assertThat(data.get("createdOn"), is(notNullValue()));

    Optional<UserEntity> persisted = userRepository.findById(USER_ID);
    assertThat(persisted.isPresent(), is(true));
    assertThat(persisted.get().getCurrentPosition(), is(CURRENT_POSITION));
    assertThat(persisted.get().getFullName(), is(FULL_NAME));
    assertThat(persisted.get().getType(), is(UserType.CANDIDATE));
  }

  @Test
  void createUserReturnsUnauthorizedWhenIamRejectsToken() throws Exception {
    stubIamInvalidToken();

    HttpResponse<String> response = postCreateUser();

    assertThat(response.statusCode(), is(401));
    assertThat(userRepository.findById(USER_ID).isPresent(), is(false));
  }

  @Test
  void getUserAuthenticatesAndReturnsFullProfile() throws Exception {
    stubIamValidateToken();
    stubIamUserInfo();
    seedCandidateUser();

    HttpResponse<String> response = getUser();

    assertThat(response.statusCode(), is(200));
    Map<String, Object> body = parse(response.body());
    assertThat(body.get("success"), is(true));

    @SuppressWarnings("unchecked")
    Map<String, Object> data = (Map<String, Object>) body.get("data");
    assertThat(data.get("id"), is(USER_ID));
    assertThat(data.get("userType"), is(UserType.CANDIDATE.name()));
    assertThat(data.get("fullName"), is(FULL_NAME));
    assertThat(data.get("userProfileImageUrl"), is(PROFILE_IMAGE_URL));
    assertThat(data.get("currentPosition"), is(CURRENT_POSITION));
    assertThat(data.get("about"), is(ABOUT));
    assertThat(data.get("githubUrl"), is(GITHUB_URL));
    assertThat(data.get("linkedinUrl"), is(LINKEDIN_URL));
    assertThat(data.get("birthDate"), is(BIRTH_DATE.toString()));
    assertThat(data.get("createdOn"), is(notNullValue()));
  }

  @Test
  void getUserReturnsNotFoundWhenUserDoesNotExist() throws Exception {
    stubIamValidateToken();
    stubIamUserInfo();

    HttpResponse<String> response = getUser();

    assertThat(response.statusCode(), is(404));
    assertThat(parse(response.body()).get("success"), is(false));
  }

  @Test
  void getUserReturnsUnauthorizedWhenIamRejectsToken() throws Exception {
    stubIamInvalidToken();
    seedCandidateUser();

    HttpResponse<String> response = getUser();

    assertThat(response.statusCode(), is(401));
  }

  @Test
  void updateUserProfileAuthenticatesAppliesSuppliedFieldsAndReturnsOk() throws Exception {
    stubIamValidateToken();
    stubIamUserInfo();
    seedCandidateUser();

    UpdateUserProfileRequest updateRequest = new UpdateUserProfileRequest();
    updateRequest.setFullName(UPDATED_FULL_NAME);
    updateRequest.setCurrentPosition(UPDATED_CURRENT_POSITION);

    HttpResponse<String> response = patchUserProfile(updateRequest);

    assertThat(response.statusCode(), is(200));
    Map<String, Object> body = parse(response.body());
    assertThat(body.get("success"), is(true));

    @SuppressWarnings("unchecked")
    Map<String, Object> data = (Map<String, Object>) body.get("data");
    assertThat(data.get("id"), is(USER_ID));
    assertThat(data.get("fullName"), is(UPDATED_FULL_NAME));
    assertThat(data.get("currentPosition"), is(UPDATED_CURRENT_POSITION));
    assertThat(data.get("about"), is(ABOUT));

    UserEntity persisted = userRepository.findById(USER_ID).orElseThrow();
    assertThat(persisted.getFullName(), is(UPDATED_FULL_NAME));
    assertThat(persisted.getCurrentPosition(), is(UPDATED_CURRENT_POSITION));
    assertThat(persisted.getAbout(), is(ABOUT));
  }

  @Test
  void updateUserProfileReturnsNotFoundWhenUserDoesNotExist() throws Exception {
    stubIamValidateToken();
    stubIamUserInfo();

    UpdateUserProfileRequest updateRequest = new UpdateUserProfileRequest();
    updateRequest.setFullName(UPDATED_FULL_NAME);

    HttpResponse<String> response = patchUserProfile(updateRequest);

    assertThat(response.statusCode(), is(404));
    assertThat(parse(response.body()).get("success"), is(false));
  }

  @Test
  void updateUserProfileReturnsUnauthorizedWhenIamRejectsToken() throws Exception {
    stubIamInvalidToken();
    seedCandidateUser();

    UpdateUserProfileRequest updateRequest = new UpdateUserProfileRequest();
    updateRequest.setFullName(UPDATED_FULL_NAME);

    HttpResponse<String> response = patchUserProfile(updateRequest);

    assertThat(response.statusCode(), is(401));
    assertThat(userRepository.findById(USER_ID).orElseThrow().getFullName(), is(FULL_NAME));
  }

  @Test
  void createUserWithCompanyAuthenticatesPersistsOwnerAndCompanyAndReturnsCreated() throws Exception {
    stubIamValidateToken();
    stubIamUserInfo();

    HttpResponse<String> response = postCreateUserWithCompany();

    assertThat(response.statusCode(), is(201));
    Map<String, Object> body = parse(response.body());
    assertThat(body.get("success"), is(true));

    @SuppressWarnings("unchecked")
    Map<String, Object> data = (Map<String, Object>) body.get("data");
    String companyId = (String) data.get("companyId");
    assertThat(companyId, is(notNullValue()));
    assertThat(data.get("companyName"), is(COMPANY_NAME));
    assertThat(data.get("companyAbout"), is(COMPANY_ABOUT));
    assertThat(data.get("companySize"), is(COMPANY_SIZE));
    assertThat(data.get("companyProfileImageUrl"), is(COMPANY_PROFILE_IMAGE_URL));
    assertThat(data.get("companyCreatedOn"), is(notNullValue()));
    assertThat(data.get("ownerUserId"), is(USER_ID));

    Optional<CompanyEntity> persistedCompany = companyRepository.findById(companyId);
    assertThat(persistedCompany.isPresent(), is(true));
    assertThat(persistedCompany.get().getName(), is(COMPANY_NAME));
    assertThat(persistedCompany.get().getSize(), is(COMPANY_SIZE));

    Optional<UserEntity> persistedOwner = userRepository.findById(USER_ID);
    assertThat(persistedOwner.isPresent(), is(true));
    assertThat(persistedOwner.get().getType(), is(UserType.COMPANY));
    assertThat(persistedOwner.get().getCurrentPosition(), is(CURRENT_POSITION));
  }

  @Test
  void createUserWithCompanyReturnsBadRequestWhenCompanyNameAlreadyExists() throws Exception {
    stubIamValidateToken();
    stubIamUserInfo();
    companyRepository.save(CompanyEntity.builder()
        .id("22222222-2222-2222-2222-222222222222")
        .name(COMPANY_NAME)
        .createdOn(LocalDate.now())
        .build());

    HttpResponse<String> response = postCreateUserWithCompany();

    assertThat(response.statusCode(), is(400));
    assertThat(parse(response.body()).get("success"), is(false));
    // the owner must not be persisted when the company name is rejected
    assertThat(userRepository.findById(USER_ID).isPresent(), is(false));
    assertThat(companyRepository.count(), is(1L));
  }

  @Test
  void createUserWithCompanyReturnsUnauthorizedWhenIamRejectsToken() throws Exception {
    stubIamInvalidToken();

    HttpResponse<String> response = postCreateUserWithCompany();

    assertThat(response.statusCode(), is(401));
    assertThat(userRepository.findById(USER_ID).isPresent(), is(false));
    assertThat(companyRepository.count(), is(0L));
  }

  @Test
  void createJobPersistsJobForCompanyOwnerAndReturnsCreated() throws Exception {
    stubIamValidateToken();
    stubIamUserInfo();
    seedCompanyOwnerWithCompany();

    HttpResponse<String> response = postCreateJob();

    assertThat(response.statusCode(), is(201));
    Map<String, Object> body = parse(response.body());
    assertThat(body.get("success"), is(true));

    @SuppressWarnings("unchecked")
    Map<String, Object> data = (Map<String, Object>) body.get("data");
    String jobId = (String) data.get("jobId");
    assertThat(jobId, is(notNullValue()));
    assertThat(data.get("title"), is(JOB_TITLE));
    assertThat(data.get("description"), is(JOB_DESCRIPTION));
    assertThat(data.get("workType"), is(JOB_WORK_TYPE.name()));
    assertThat(data.get("salaryRangeDown"), is(JOB_SALARY_RANGE_DOWN));
    assertThat(data.get("salaryRangeTop"), is(JOB_SALARY_RANGE_TOP));
    assertThat(data.get("jobPostSource"), is(JOB_POST_TYPE.name()));
    assertThat(data.get("createdOn"), is(notNullValue()));
    assertThat(data.get("companyId"), is(COMPANY_ID));
    assertThat(data.get("createdByUserId"), is(USER_ID));

    Optional<JobPostEntity> persisted = jobPostRepository.findById(jobId);
    assertThat(persisted.isPresent(), is(true));
    assertThat(persisted.get().getTitle(), is(JOB_TITLE));
    assertThat(persisted.get().getWorkType(), is(JOB_WORK_TYPE));
    assertThat(persisted.get().getCompany().getId(), is(COMPANY_ID));
    assertThat(persisted.get().getCreatedByUser().getId(), is(USER_ID));
  }

  @Test
  void createJobPersistsJobWithSkillsWhenSkillsProvided() throws Exception {
    stubIamValidateToken();
    stubIamUserInfo();
    seedCompanyOwnerWithCompany();

    HttpResponse<String> response = postCreateJob(JOB_SKILLS);

    assertThat(response.statusCode(), is(201));
    Map<String, Object> body = parse(response.body());

    @SuppressWarnings("unchecked")
    Map<String, Object> data = (Map<String, Object>) body.get("data");
    String jobId = (String) data.get("jobId");
    assertThat(data.get("skills"), is(JOB_SKILLS));

    assertThat(jobPostSkillRepository.count(), is(3L));
    List<String> persistedSkills = jobPostSkillRepository.findAll().stream()
        .filter(skill -> skill.getJobPost().getId().equals(jobId))
        .map(skill -> skill.getSkill())
        .sorted()
        .toList();
    assertThat(persistedSkills, contains("Java", "PostgreSQL", "Spring"));
  }

  @Test
  void createJobReturnsBadRequestWhenUserIsNotCompanyType() throws Exception {
    stubIamValidateToken();
    stubIamUserInfo();
    userRepository.save(UserEntity.builder()
        .id(USER_ID)
        .type(UserType.CANDIDATE)
        .createdOn(LocalDate.now())
        .build());

    HttpResponse<String> response = postCreateJob();

    assertThat(response.statusCode(), is(400));
    assertThat(parse(response.body()).get("success"), is(false));
    assertThat(jobPostRepository.count(), is(0L));
  }

  @Test
  void createJobReturnsBadRequestWhenCompanyOwnerHasNoCompany() throws Exception {
    stubIamValidateToken();
    stubIamUserInfo();
    userRepository.save(UserEntity.builder()
        .id(USER_ID)
        .type(UserType.COMPANY)
        .createdOn(LocalDate.now())
        .build());

    HttpResponse<String> response = postCreateJob();

    assertThat(response.statusCode(), is(400));
    assertThat(parse(response.body()).get("success"), is(false));
    assertThat(jobPostRepository.count(), is(0L));
  }

  @Test
  void createJobReturnsUnauthorizedWhenIamRejectsToken() throws Exception {
    stubIamInvalidToken();
    seedCompanyOwnerWithCompany();

    HttpResponse<String> response = postCreateJob();

    assertThat(response.statusCode(), is(401));
    assertThat(jobPostRepository.count(), is(0L));
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

  private void seedCandidateUser() {
    userRepository.save(UserEntity.builder()
        .id(USER_ID)
        .type(UserType.CANDIDATE)
        .fullName(FULL_NAME)
        .profileImageUrl(PROFILE_IMAGE_URL)
        .currentPosition(CURRENT_POSITION)
        .about(ABOUT)
        .githubUrl(GITHUB_URL)
        .linkedinUrl(LINKEDIN_URL)
        .birthDate(BIRTH_DATE)
        .createdOn(LocalDate.now())
        .build());
  }

  private HttpResponse<String> getUser() throws Exception {
    HttpRequest httpRequest = HttpRequest.newBuilder()
        .uri(URI.create(createUserUrl()))
        .header(AUTHORIZATION_HEADER, BEARER_TOKEN)
        .header(REQUEST_UUID_HEADER, REQUEST_UUID)
        .GET()
        .build();
    return httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
  }

  private HttpResponse<String> patchUserProfile(UpdateUserProfileRequest updateRequest) throws Exception {
    HttpRequest httpRequest = HttpRequest.newBuilder()
        .uri(URI.create(createUserUrl()))
        .header(CONTENT_TYPE_HEADER, APPLICATION_JSON)
        .header(AUTHORIZATION_HEADER, BEARER_TOKEN)
        .header(REQUEST_UUID_HEADER, REQUEST_UUID)
        .method("PATCH", HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(updateRequest)))
        .build();
    return httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
  }

  private HttpResponse<String> postCreateUser() throws Exception {
    HttpRequest httpRequest = HttpRequest.newBuilder()
        .uri(URI.create(createUserUrl()))
        .header(CONTENT_TYPE_HEADER, APPLICATION_JSON)
        .header(AUTHORIZATION_HEADER, BEARER_TOKEN)
        .header(REQUEST_UUID_HEADER, REQUEST_UUID)
        .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(validRequest())))
        .build();
    return httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
  }

  private HttpResponse<String> postCreateUserWithCompany() throws Exception {
    HttpRequest httpRequest = HttpRequest.newBuilder()
        .uri(URI.create(createCompanyUrl()))
        .header(CONTENT_TYPE_HEADER, APPLICATION_JSON)
        .header(AUTHORIZATION_HEADER, BEARER_TOKEN)
        .header(REQUEST_UUID_HEADER, REQUEST_UUID)
        .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(validCompanyRequest())))
        .build();
    return httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
  }

  private HttpResponse<String> postCreateJob() throws Exception {
    return postCreateJob(null);
  }

  private HttpResponse<String> postCreateJob(List<String> skills) throws Exception {
    HttpRequest httpRequest = HttpRequest.newBuilder()
        .uri(URI.create(createJobUrl()))
        .header(CONTENT_TYPE_HEADER, APPLICATION_JSON)
        .header(AUTHORIZATION_HEADER, BEARER_TOKEN)
        .header(REQUEST_UUID_HEADER, REQUEST_UUID)
        .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(validJobRequest(skills))))
        .build();
    return httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> parse(String json) {
    return objectMapper.readValue(json, Map.class);
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

  private String createUserUrl() {
    return "http://localhost:" + port + CONTEXT_PATH + CREATE_USER_PATH;
  }

  private String createCompanyUrl() {
    return "http://localhost:" + port + CONTEXT_PATH + CREATE_COMPANY_PATH;
  }

  private String createJobUrl() {
    return "http://localhost:" + port + CONTEXT_PATH + CREATE_JOB_PATH;
  }

  private CreateUserRequest validRequest() {
    CreateUserRequest request = new CreateUserRequest();
    request.setUserType(UserType.CANDIDATE);
    request.setFullName(FULL_NAME);
    request.setUserProfileImageUrl(PROFILE_IMAGE_URL);
    request.setCurrentPosition(CURRENT_POSITION);
    request.setAbout(ABOUT);
    request.setGithubUrl(GITHUB_URL);
    request.setLinkedinUrl(LINKEDIN_URL);
    request.setBirthDate(BIRTH_DATE);
    return request;
  }

  private CreateUserWithCompanyRequest validCompanyRequest() {
    CreateUserWithCompanyRequest request = new CreateUserWithCompanyRequest();
    request.setUserProfileImageUrl(PROFILE_IMAGE_URL);
    request.setCurrentPosition(CURRENT_POSITION);
    request.setUserAbout(ABOUT);
    request.setLinkedinUrl(LINKEDIN_URL);
    request.setBirthDate(BIRTH_DATE);
    request.setCompanyName(COMPANY_NAME);
    request.setCompanyAbout(COMPANY_ABOUT);
    request.setCompanySize(COMPANY_SIZE);
    request.setCompanyProfileImageUrl(COMPANY_PROFILE_IMAGE_URL);
    return request;
  }

  private CreateJobRequest validJobRequest(List<String> skills) {
    CreateJobRequest request = new CreateJobRequest();
    request.setTitle(JOB_TITLE);
    request.setDescription(JOB_DESCRIPTION);
    request.setWorkType(JOB_WORK_TYPE);
    request.setSalaryRangeDown(JOB_SALARY_RANGE_DOWN);
    request.setSalaryRangeTop(JOB_SALARY_RANGE_TOP);
    request.setJobPostSource(JOB_POST_TYPE);
    request.setSkills(skills);
    return request;
  }
}