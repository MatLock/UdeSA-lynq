package com.lynq.backend;

import com.lynq.backend.controller.request.CreateJobRequest;
import com.lynq.backend.controller.request.CreateUserRequest;
import com.lynq.backend.controller.request.CreateUserWithCompanyRequest;
import com.lynq.backend.controller.request.SkillEnhanceRequest;
import com.lynq.backend.controller.request.UpdateCompanyRequest;
import com.lynq.backend.controller.request.UpdateUserProfileRequest;
import com.lynq.backend.enums.JobPostSource;
import com.lynq.backend.enums.JobStatus;
import com.lynq.backend.enums.Language;
import com.lynq.backend.enums.UserType;
import com.lynq.backend.enums.WorkType;
import com.lynq.backend.model.CompanyEntity;
import com.lynq.backend.model.JobPostEntity;
import com.lynq.backend.model.JobPostSkillEntity;
import com.lynq.backend.model.UserApplicationJobEntity;
import com.lynq.backend.model.UserEntity;
import com.lynq.backend.model.UserResumeEntity;
import com.lynq.backend.model.UserSkillsEntity;
import com.lynq.backend.repository.CompanyRepository;
import com.lynq.backend.repository.JobPostRepository;
import com.lynq.backend.repository.JobPostSkillRepository;
import com.lynq.backend.repository.UserApplicationJobRepository;
import com.lynq.backend.repository.UserRepository;
import com.lynq.backend.repository.UserResumeRepository;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
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
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;
import static org.mockserver.model.StringBody.subString;

class BackendAppApplicationTests extends AbstractE2ETest {

  private static final String CONTEXT_PATH = "/lynq-app-backend";
  private static final String CREATE_USER_PATH = "/user";
  private static final String GENERATE_UPLOAD_IMAGE_PATH = "/user/generate-upload-image";
  private static final String CREATE_COMPANY_PATH = "/company";
  private static final String CREATE_JOB_PATH = "/job";
  private static final String RESUME_PATH = "/user/resume";
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

  private static final String CURRENT_POSITION = "Backend Engineer";
  private static final String FULL_NAME = "Jane Doe";
  private static final String UPDATED_FULL_NAME = "Jane Q. Doe";
  private static final String UPDATED_CURRENT_POSITION = "Staff Engineer";
  private static final String ABOUT = "Java developer focused on distributed systems.";
  private static final String PROFILE_IMAGE_URL = "https://cdn.lynq.com/avatars/jane.png";
  private static final String GITHUB_URL = "https://github.com/janedoe";
  private static final String LINKEDIN_URL = "https://linkedin.com/in/janedoe";
  private static final LocalDate BIRTH_DATE = LocalDate.of(1995, 4, 12);
  private static final String UPLOAD_FILE_NAME = "avatar.png";

  private static final String COMPANY_NAME = "Lynq Technologies";
  private static final String COMPANY_ABOUT = "We build talent matching platforms.";
  private static final Integer COMPANY_SIZE = 250;
  private static final String COMPANY_PROFILE_IMAGE_URL = "https://cdn.lynq.com/logos/lynq.png";
  private static final String COMPANY_ID = "22222222-2222-2222-2222-222222222222";
  private static final String UPDATED_COMPANY_NAME = "Lynq Labs";
  private static final String UPDATED_COMPANY_ABOUT = "Now hiring across the globe.";
  private static final Integer UPDATED_COMPANY_SIZE = 500;
  private static final String OTHER_USER_ID = "33333333-3333-3333-3333-333333333333";
  private static final String OTHER_COMPANY_ID = "44444444-4444-4444-4444-444444444444";
  private static final String OTHER_COMPANY_NAME = "Taken Name Inc";

  private static final String JOB_TITLE = "Senior Backend Engineer";
  private static final String JOB_DESCRIPTION = "Build and scale the Lynq hiring platform.";
  private static final WorkType JOB_WORK_TYPE = WorkType.REMOTE;
  private static final Integer JOB_SALARY_RANGE_DOWN = 80000;
  private static final Integer JOB_SALARY_RANGE_TOP = 120000;
  private static final JobPostSource JOB_POST_TYPE = JobPostSource.LYNQ;
  private static final String SKILL_JAVA = "Java";
  private static final String SKILL_SPRING = "Spring";
  private static final String SKILL_POSTGRES = "PostgreSQL";
  private static final String SKILL_REACT = "React";
  private static final String SKILL_GO = "Go";
  private static final String SKILL_KOTLIN = "Kotlin";
  private static final String SKILL_PYTHON = "Python";
  private static final String SKILL_SALES = "Sales";
  private static final List<String> JOB_SKILLS = List.of(SKILL_JAVA, SKILL_SPRING, SKILL_POSTGRES);

  private static final String POSTER_FULL_NAME = "Nora Poster";
  private static final String POSTER_CURRENT_POSITION = "Hiring Manager";
  private static final String SECOND_USER_ID = "44444444-4444-4444-4444-444444444444";
  private static final String SECOND_COMPANY_ID = "33333333-3333-3333-3333-333333333333";
  private static final String SECOND_COMPANY_NAME = "Globex Corporation";
  private static final String JOB_ID_A = "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa";
  private static final String JOB_ID_B = "bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb";
  private static final String JOB_ID_C = "cccccccc-cccc-cccc-cccc-cccccccccccc";
  private static final LocalDate JOB_CREATED_OLD = LocalDate.of(2026, 6, 1);
  private static final LocalDate JOB_CREATED_MID = LocalDate.of(2026, 6, 10);
  private static final LocalDate JOB_CREATED_NEW = LocalDate.of(2026, 6, 20);
  private static final String PLACEHOLDER_DESCRIPTION = "d";
  private static final String PRE_SIGNED_URL_SIGNATURE_MARKER = "X-Amz-Signature";
  private static final String LISTED_NEWEST_JOB_TITLE = "Frontend Engineer";

  private static final String JOB_ID = "77777777-7777-7777-7777-777777777777";
  private static final String UNKNOWN_JOB_ID = "99999999-9999-9999-9999-999999999999";
  private static final Long INITIAL_SEEN = 4L;
  private static final int DEFAULT_PAGE_SIZE = 10;
  private static final int MATCHING_LYNQ_SCORE = 50;

  private static final String CANDIDATE_A_ID = "a1a1a1a1-a1a1-a1a1-a1a1-a1a1a1a1a1a1";
  private static final String CANDIDATE_A_NAME = "Alice Applicant";
  private static final String CANDIDATE_A_POSITION = "Backend Engineer";
  private static final String CANDIDATE_B_ID = "b1b1b1b1-b1b1-b1b1-b1b1-b1b1b1b1b1b1";
  private static final String CANDIDATE_B_NAME = "Bob Applicant";
  private static final String CANDIDATE_B_POSITION = "Frontend Engineer";
  private static final String APPLICATION_NEWEST_ID = "a4a4a4a4-a4a4-a4a4-a4a4-a4a4a4a4a4a4";
  private static final String APPLICATION_OLDEST_ID = "a5a5a5a5-a5a5-a5a5-a5a5-a5a5a5a5a5a5";

  private static final String RESUME_ID = "66666666-6666-6666-6666-666666666666";
  private static final String RESUME_NAME = "Jane Doe - Backend";
  private static final Language RESUME_LANGUAGE = Language.EN;
  private static final String RESUME_SUMMARY = "Backend engineer";
  private static final int RESUME_YEARS = 8;
  private static final String RESUME_JSON =
      "{\"summary\":\"" + RESUME_SUMMARY + "\",\"years\":" + RESUME_YEARS + "}";
  private static final String RESUME_STORAGE_PATH = "lynq/users/" + USER_ID + "/resume/cv.pdf";

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

  @Autowired
  private UserApplicationJobRepository userApplicationJobRepository;

  @Autowired
  private UserResumeRepository userResumeRepository;

  private final HttpClient httpClient = HttpClient.newHttpClient();

  @BeforeEach
  void setUp() {
    lynqIamMock.reset();
    lynqMlMock.reset();
    userApplicationJobRepository.deleteAll();
    userResumeRepository.deleteAll();
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
    // the stored profile image reference is returned as a pre-signed download URL
    String userProfileImageUrl = (String) data.get("userProfileImageUrl");
    assertThat(userProfileImageUrl, is(notNullValue()));
    assertThat(userProfileImageUrl, containsString(PRE_SIGNED_URL_SIGNATURE_MARKER));
    assertThat(userProfileImageUrl, containsString(PROFILE_IMAGE_URL.substring("https://".length())));
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
  void updateCompanyAuthenticatesAppliesSuppliedFieldsPersistsAndReturnsOk() throws Exception {
    stubIamValidateToken();
    stubIamUserInfo();
    seedCompanyOwnerWithCompany();

    UpdateCompanyRequest updateRequest = new UpdateCompanyRequest();
    updateRequest.setName(UPDATED_COMPANY_NAME);
    updateRequest.setAbout(UPDATED_COMPANY_ABOUT);
    updateRequest.setSize(UPDATED_COMPANY_SIZE);

    HttpResponse<String> response = patchCompany(updateRequest);

    assertThat(response.statusCode(), is(200));
    Map<String, Object> body = parse(response.body());
    assertThat(body.get("success"), is(true));

    @SuppressWarnings("unchecked")
    Map<String, Object> data = (Map<String, Object>) body.get("data");
    assertThat(data.get("id"), is(COMPANY_ID));
    assertThat(data.get("name"), is(UPDATED_COMPANY_NAME));
    assertThat(data.get("about"), is(UPDATED_COMPANY_ABOUT));
    assertThat(data.get("size"), is(UPDATED_COMPANY_SIZE));

    CompanyEntity persisted = companyRepository.findById(COMPANY_ID).orElseThrow();
    assertThat(persisted.getName(), is(UPDATED_COMPANY_NAME));
    assertThat(persisted.getAbout(), is(UPDATED_COMPANY_ABOUT));
    assertThat(persisted.getSize(), is(UPDATED_COMPANY_SIZE));
  }

  @Test
  void updateCompanyReturnsNotFoundWhenUserOwnsNoCompany() throws Exception {
    stubIamValidateToken();
    stubIamUserInfo();
    seedCandidateUser();

    UpdateCompanyRequest updateRequest = new UpdateCompanyRequest();
    updateRequest.setAbout(UPDATED_COMPANY_ABOUT);

    HttpResponse<String> response = patchCompany(updateRequest);

    assertThat(response.statusCode(), is(404));
    assertThat(parse(response.body()).get("success"), is(false));
  }

  @Test
  void updateCompanyReturnsBadRequestWhenNewNameIsAlreadyTaken() throws Exception {
    stubIamValidateToken();
    stubIamUserInfo();
    seedCompanyOwnerWithCompany();
    UserEntity otherOwner = seedCompanyUser(OTHER_USER_ID, FULL_NAME, CURRENT_POSITION,
        COMPANY_PROFILE_IMAGE_URL);
    seedCompany(OTHER_COMPANY_ID, OTHER_COMPANY_NAME, otherOwner);

    UpdateCompanyRequest updateRequest = new UpdateCompanyRequest();
    updateRequest.setName(OTHER_COMPANY_NAME);

    HttpResponse<String> response = patchCompany(updateRequest);

    assertThat(response.statusCode(), is(400));
    assertThat(parse(response.body()).get("success"), is(false));
    assertThat(companyRepository.findById(COMPANY_ID).orElseThrow().getName(), is(COMPANY_NAME));
  }

  @Test
  void updateCompanyReturnsUnauthorizedWhenIamRejectsToken() throws Exception {
    stubIamInvalidToken();
    seedCompanyOwnerWithCompany();

    UpdateCompanyRequest updateRequest = new UpdateCompanyRequest();
    updateRequest.setName(UPDATED_COMPANY_NAME);

    HttpResponse<String> response = patchCompany(updateRequest);

    assertThat(response.statusCode(), is(401));
    assertThat(companyRepository.findById(COMPANY_ID).orElseThrow().getName(), is(COMPANY_NAME));
  }

  @Test
  void generateUploadImageUrlReturnsPreSignedUrlThatUploadsToS3AndPersistsThePath() throws Exception {
    stubIamValidateToken();
    stubIamUserInfo();
    seedCandidateUser();

    HttpResponse<String> response = getGenerateUploadImageUrl(UPLOAD_FILE_NAME);

    assertThat(response.statusCode(), is(200));
    Map<String, Object> body = parse(response.body());
    assertThat(body.get("success"), is(true));

    @SuppressWarnings("unchecked")
    Map<String, Object> data = (Map<String, Object>) body.get("data");
    String preSignedUrl = (String) data.get("preSignedUrl");
    assertThat(preSignedUrl, is(notNullValue()));

    // the generated S3 path is persisted as the user's profile image reference
    String expectedKey = "lynq/users/" + USER_ID + "/profile/" + UPLOAD_FILE_NAME;
    assertThat(userRepository.findById(USER_ID).orElseThrow().getProfileImageUrl(), is(expectedKey));

    // the frontend pushes the binary straight to S3 using the pre-signed URL
    byte[] imageBytes = "profile-image-binary".getBytes();
    HttpResponse<Void> uploadResponse = httpClient.send(
        HttpRequest.newBuilder()
            .uri(URI.create(preSignedUrl))
            .PUT(HttpRequest.BodyPublishers.ofByteArray(imageBytes))
            .build(),
        HttpResponse.BodyHandlers.discarding());
    assertThat(uploadResponse.statusCode(), is(200));

    // and the object can be obtained back from S3 at the persisted path
    byte[] storedBytes = s3TestClient.getObjectAsBytes(
        GetObjectRequest.builder().bucket(AWS_BUCKET).key(expectedKey).build()).asByteArray();
    assertThat(storedBytes, is(imageBytes));
  }

  @Test
  void generateUploadImageUrlReturnsUnauthorizedWhenIamRejectsToken() throws Exception {
    stubIamInvalidToken();
    seedCandidateUser();

    HttpResponse<String> response = getGenerateUploadImageUrl(UPLOAD_FILE_NAME);

    assertThat(response.statusCode(), is(401));
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
    assertThat(persistedSkills, contains(SKILL_JAVA, SKILL_POSTGRES, SKILL_SPRING));
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

  @Test
  void getJobsReturnsAvailableJobsNewestFirstWithCompanyAndPosterAndSkipsUnavailable()
      throws Exception {
    stubIamValidateToken();
    stubIamUserInfo();
    UserEntity poster = seedCompanyUser(USER_ID, POSTER_FULL_NAME, POSTER_CURRENT_POSITION,
        PROFILE_IMAGE_URL);
    CompanyEntity company = seedCompany(COMPANY_ID, COMPANY_NAME, poster);
    seedJob(JOB_ID_A, "Backend Engineer", "Older post", WorkType.REMOTE, JOB_CREATED_OLD, JobStatus.OPEN,
        company, poster, List.of(SKILL_JAVA));
    seedJob(JOB_ID_B, LISTED_NEWEST_JOB_TITLE, "Newer post", WorkType.IN_OFFICE, JOB_CREATED_NEW,
        JobStatus.OPEN, company, poster, List.of(SKILL_REACT));
    seedJob(JOB_ID_C, "Hidden Engineer", "Unavailable post", WorkType.REMOTE, JOB_CREATED_MID,
        JobStatus.CLOSE, company, poster, List.of(SKILL_GO));

    HttpResponse<String> response = getJobs(null);

    assertThat(response.statusCode(), is(200));
    Map<String, Object> body = parse(response.body());
    assertThat(body.get("success"), is(true));

    Map<String, Object> data = dataOf(body);
    assertThat(data.get("totalElements"), is(2));
    List<Map<String, Object>> content = contentOf(data);
    assertThat(jobIdsOf(content), contains(JOB_ID_B, JOB_ID_A));

    Map<String, Object> newest = content.get(0);
    assertThat(newest.get("title"), is(LISTED_NEWEST_JOB_TITLE));
    assertThat(newest.get("workType"), is(WorkType.IN_OFFICE.name()));

    @SuppressWarnings("unchecked")
    Map<String, Object> companyData = (Map<String, Object>) newest.get("company");
    assertThat(companyData.get("id"), is(COMPANY_ID));
    assertThat(companyData.get("name"), is(COMPANY_NAME));
    assertThat(companyData.get("about"), is(COMPANY_ABOUT));
    assertThat(companyData.get("size"), is(COMPANY_SIZE));
    // the stored company logo reference is returned as a pre-signed download URL
    String companyProfileImageUrl = (String) companyData.get("profileImageUrl");
    assertThat(companyProfileImageUrl, is(notNullValue()));
    assertThat(companyProfileImageUrl, containsString(PRE_SIGNED_URL_SIGNATURE_MARKER));
    assertThat(companyProfileImageUrl,
        containsString(COMPANY_PROFILE_IMAGE_URL.substring("https://".length())));

    @SuppressWarnings("unchecked")
    Map<String, Object> postedBy = (Map<String, Object>) newest.get("postedBy");
    assertThat(postedBy.get("id"), is(USER_ID));
    assertThat(postedBy.get("fullName"), is(POSTER_FULL_NAME));
    assertThat(postedBy.get("currentPosition"), is(POSTER_CURRENT_POSITION));
    // the stored profile image reference is returned as a pre-signed download URL
    String posterProfileImageUrl = (String) postedBy.get("profileImageUrl");
    assertThat(posterProfileImageUrl, is(notNullValue()));
    assertThat(posterProfileImageUrl, containsString(PRE_SIGNED_URL_SIGNATURE_MARKER));
    assertThat(posterProfileImageUrl,
        containsString(PROFILE_IMAGE_URL.substring("https://".length())));

    @SuppressWarnings("unchecked")
    List<String> skills = (List<String>) newest.get("skills");
    assertThat(skills, contains(SKILL_REACT));
  }

  @Test
  void getJobsPaginatesResultsNewestFirst() throws Exception {
    stubIamValidateToken();
    stubIamUserInfo();
    UserEntity poster = seedCompanyUser(USER_ID, POSTER_FULL_NAME, POSTER_CURRENT_POSITION, null);
    CompanyEntity company = seedCompany(COMPANY_ID, COMPANY_NAME, poster);
    seedJob(JOB_ID_A, "First", PLACEHOLDER_DESCRIPTION, WorkType.REMOTE, JOB_CREATED_OLD, JobStatus.OPEN, company, poster, null);
    seedJob(JOB_ID_B, "Second", PLACEHOLDER_DESCRIPTION, WorkType.REMOTE, JOB_CREATED_MID, JobStatus.OPEN, company, poster, null);
    seedJob(JOB_ID_C, "Third", PLACEHOLDER_DESCRIPTION, WorkType.REMOTE, JOB_CREATED_NEW, JobStatus.OPEN, company, poster, null);

    Map<String, Object> firstPage = dataOf(parse(getJobs("page=0&size=2").body()));
    assertThat(firstPage.get("page"), is(0));
    assertThat(firstPage.get("size"), is(2));
    assertThat(firstPage.get("totalElements"), is(3));
    assertThat(firstPage.get("totalPages"), is(2));
    assertThat(firstPage.get("hasNext"), is(true));
    assertThat(firstPage.get("hasPrevious"), is(false));
    assertThat(jobIdsOf(contentOf(firstPage)), contains(JOB_ID_C, JOB_ID_B));

    Map<String, Object> secondPage = dataOf(parse(getJobs("page=1&size=2").body()));
    assertThat(secondPage.get("hasNext"), is(false));
    assertThat(secondPage.get("hasPrevious"), is(true));
    assertThat(jobIdsOf(contentOf(secondPage)), contains(JOB_ID_A));
  }

  @Test
  void getJobsFiltersByTitleContainsCaseInsensitive() throws Exception {
    stubIamValidateToken();
    stubIamUserInfo();
    UserEntity poster = seedCompanyUser(USER_ID, POSTER_FULL_NAME, POSTER_CURRENT_POSITION, null);
    CompanyEntity company = seedCompany(COMPANY_ID, COMPANY_NAME, poster);
    seedJob(JOB_ID_A, "Senior Backend Engineer", PLACEHOLDER_DESCRIPTION, WorkType.REMOTE, JOB_CREATED_OLD, JobStatus.OPEN,
        company, poster, null);
    seedJob(JOB_ID_B, "Frontend Developer", PLACEHOLDER_DESCRIPTION, WorkType.REMOTE, JOB_CREATED_NEW, JobStatus.OPEN, company,
        poster, null);

    Map<String, Object> data = dataOf(parse(getJobs("filterValue=backend").body()));

    assertThat(data.get("totalElements"), is(1));
    assertThat(jobIdsOf(contentOf(data)), contains(JOB_ID_A));
  }

  @Test
  void getJobsFiltersByDescriptionContainsCaseInsensitive() throws Exception {
    stubIamValidateToken();
    stubIamUserInfo();
    UserEntity poster = seedCompanyUser(USER_ID, POSTER_FULL_NAME, POSTER_CURRENT_POSITION, null);
    CompanyEntity company = seedCompany(COMPANY_ID, COMPANY_NAME, poster);
    seedJob(JOB_ID_A, "Job A", "Work on distributed systems", WorkType.REMOTE, JOB_CREATED_OLD,
        JobStatus.OPEN, company, poster, null);
    seedJob(JOB_ID_B, "Job B", "Design marketing pages", WorkType.REMOTE, JOB_CREATED_NEW, JobStatus.OPEN,
        company, poster, null);

    Map<String, Object> data = dataOf(parse(getJobs("filterValue=DISTRIBUTED").body()));

    assertThat(data.get("totalElements"), is(1));
    assertThat(jobIdsOf(contentOf(data)), contains(JOB_ID_A));
  }

  @Test
  void getJobsFiltersByWorkType() throws Exception {
    stubIamValidateToken();
    stubIamUserInfo();
    UserEntity poster = seedCompanyUser(USER_ID, POSTER_FULL_NAME, POSTER_CURRENT_POSITION, null);
    CompanyEntity company = seedCompany(COMPANY_ID, COMPANY_NAME, poster);
    seedJob(JOB_ID_A, "Remote job", PLACEHOLDER_DESCRIPTION, WorkType.REMOTE, JOB_CREATED_OLD, JobStatus.OPEN, company, poster,
        null);
    seedJob(JOB_ID_B, "Office job", PLACEHOLDER_DESCRIPTION, WorkType.IN_OFFICE, JOB_CREATED_NEW, JobStatus.OPEN, company, poster,
        null);

    Map<String, Object> data = dataOf(parse(getJobs("filterValue=IN_OFFICE").body()));

    assertThat(data.get("totalElements"), is(1));
    assertThat(jobIdsOf(contentOf(data)), contains(JOB_ID_B));
  }

  @Test
  void getJobsFiltersBySkillCaseInsensitive() throws Exception {
    stubIamValidateToken();
    stubIamUserInfo();
    UserEntity poster = seedCompanyUser(USER_ID, POSTER_FULL_NAME, POSTER_CURRENT_POSITION, null);
    CompanyEntity company = seedCompany(COMPANY_ID, COMPANY_NAME, poster);
    seedJob(JOB_ID_A, "Java job", PLACEHOLDER_DESCRIPTION, WorkType.REMOTE, JOB_CREATED_OLD, JobStatus.OPEN, company, poster,
        List.of(SKILL_JAVA, SKILL_SPRING));
    seedJob(JOB_ID_B, "Python job", PLACEHOLDER_DESCRIPTION, WorkType.REMOTE, JOB_CREATED_NEW, JobStatus.OPEN, company, poster,
        List.of(SKILL_PYTHON));

    Map<String, Object> data = dataOf(parse(getJobs("filterValue=java").body()));

    assertThat(data.get("totalElements"), is(1));
    assertThat(jobIdsOf(contentOf(data)), contains(JOB_ID_A));
  }

  @Test
  void getJobsFiltersByCompanyNameContainsCaseInsensitive() throws Exception {
    stubIamValidateToken();
    stubIamUserInfo();
    UserEntity lynqOwner = seedCompanyUser(USER_ID, POSTER_FULL_NAME, POSTER_CURRENT_POSITION, null);
    CompanyEntity lynq = seedCompany(COMPANY_ID, COMPANY_NAME, lynqOwner);
    UserEntity globexOwner = seedCompanyUser(SECOND_USER_ID, "Hank", "Recruiter", null);
    CompanyEntity globex = seedCompany(SECOND_COMPANY_ID, SECOND_COMPANY_NAME, globexOwner);
    seedJob(JOB_ID_A, "Lynq job", PLACEHOLDER_DESCRIPTION, WorkType.REMOTE, JOB_CREATED_OLD, JobStatus.OPEN, lynq, lynqOwner,
        null);
    seedJob(JOB_ID_B, "Globex job", PLACEHOLDER_DESCRIPTION, WorkType.REMOTE, JOB_CREATED_NEW, JobStatus.OPEN, globex, globexOwner,
        null);

    Map<String, Object> data = dataOf(parse(getJobs("filterValue=globex").body()));

    assertThat(data.get("totalElements"), is(1));
    assertThat(jobIdsOf(contentOf(data)), contains(JOB_ID_B));
  }

  @Test
  void getJobsMatchesFilterValueAcrossColumnsWithOr() throws Exception {
    stubIamValidateToken();
    stubIamUserInfo();
    UserEntity poster = seedCompanyUser(USER_ID, POSTER_FULL_NAME, POSTER_CURRENT_POSITION, null);
    CompanyEntity company = seedCompany(COMPANY_ID, COMPANY_NAME, poster);
    seedJob(JOB_ID_A, "Kotlin Engineer", PLACEHOLDER_DESCRIPTION, WorkType.REMOTE, JOB_CREATED_OLD, JobStatus.OPEN,
        company, poster, List.of(SKILL_GO));
    seedJob(JOB_ID_B, "Designer", PLACEHOLDER_DESCRIPTION, WorkType.REMOTE, JOB_CREATED_NEW, JobStatus.OPEN,
        company, poster, List.of(SKILL_KOTLIN));
    seedJob(JOB_ID_C, "Recruiter", PLACEHOLDER_DESCRIPTION, WorkType.IN_OFFICE, JOB_CREATED_MID, JobStatus.OPEN,
        company, poster, List.of(SKILL_SALES));

    Map<String, Object> data = dataOf(parse(getJobs("filterValue=kotlin").body()));

    assertThat(data.get("totalElements"), is(2));
    assertThat(jobIdsOf(contentOf(data)), contains(JOB_ID_B, JOB_ID_A));
  }

  @Test
  void getJobsReturnsUnauthorizedWhenIamRejectsToken() throws Exception {
    stubIamInvalidToken();

    HttpResponse<String> response = getJobs(null);

    assertThat(response.statusCode(), is(401));
  }

  private UserEntity seedCompanyUser(String id, String fullName, String currentPosition,
      String profileImageUrl) {
    return userRepository.save(UserEntity.builder()
        .id(id)
        .type(UserType.COMPANY)
        .fullName(fullName)
        .currentPosition(currentPosition)
        .profileImageUrl(profileImageUrl)
        .createdOn(LocalDate.now())
        .build());
  }

  private CompanyEntity seedCompany(String companyId, String name, UserEntity owner) {
    return companyRepository.save(CompanyEntity.builder()
        .id(companyId)
        .name(name)
        .about(COMPANY_ABOUT)
        .size(COMPANY_SIZE)
        .profileImageUrl(COMPANY_PROFILE_IMAGE_URL)
        .createdOn(LocalDate.now())
        .owner(owner)
        .build());
  }

  private JobPostEntity seedJob(String id, String title, String description, WorkType workType,
      LocalDate createdOn, JobStatus jobStatus, CompanyEntity company, UserEntity poster,
      List<String> skills) {
    JobPostEntity job = JobPostEntity.builder()
        .id(id)
        .title(title)
        .description(description)
        .workType(workType)
        .jobPostSource(JOB_POST_TYPE)
        .createdOn(createdOn)
        .jobStatus(jobStatus)
        .company(company)
        .createdByUser(poster)
        .build();
    if (skills != null) {
      skills.forEach(skill -> job.getSkills().add(JobPostSkillEntity.builder()
          .id(UUID.randomUUID().toString())
          .jobPost(job)
          .skill(skill)
          .build()));
    }
    return jobPostRepository.save(job);
  }

  private HttpResponse<String> getJobs(String queryString) throws Exception {
    String url = createJobUrl()
        + (queryString == null || queryString.isBlank() ? "" : "?" + queryString);
    HttpRequest httpRequest = HttpRequest.newBuilder()
        .uri(URI.create(url))
        .header(AUTHORIZATION_HEADER, BEARER_TOKEN)
        .header(REQUEST_UUID_HEADER, REQUEST_UUID)
        .GET()
        .build();
    return httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> dataOf(Map<String, Object> body) {
    return (Map<String, Object>) body.get("data");
  }

  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> contentOf(Map<String, Object> data) {
    return (List<Map<String, Object>>) data.get("content");
  }

  private List<String> jobIdsOf(List<Map<String, Object>> content) {
    return content.stream().map(item -> (String) item.get("jobId")).toList();
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

  private HttpResponse<String> getGenerateUploadImageUrl(String fileName) throws Exception {
    HttpRequest httpRequest = HttpRequest.newBuilder()
        .uri(URI.create(generateUploadImageUrl(fileName)))
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

  private HttpResponse<String> patchCompany(UpdateCompanyRequest updateRequest) throws Exception {
    HttpRequest httpRequest = HttpRequest.newBuilder()
        .uri(URI.create(createCompanyUrl()))
        .header(CONTENT_TYPE_HEADER, APPLICATION_JSON)
        .header(AUTHORIZATION_HEADER, BEARER_TOKEN)
        .header(REQUEST_UUID_HEADER, REQUEST_UUID)
        .method("PATCH", HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(updateRequest)))
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

  private String generateUploadImageUrl(String fileName) {
    return "http://localhost:" + port + CONTEXT_PATH + GENERATE_UPLOAD_IMAGE_PATH + "?file-name=" + fileName;
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
    request.setFullName(FULL_NAME);
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

  // ---------------------------------------------------------------------------
  // ML skill-enhance proxy
  // ---------------------------------------------------------------------------

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

    // the outbound body honours @JsonProperty("work_type") on the ML client DTO:
    // lynq-ml (pydantic) receives the snake_cased key, not the camelCase field name
    lynqMlMock.verify(request()
        .withMethod("POST")
        .withPath(ML_SKILL_ENHANCE_PATH)
        .withHeader(REQUEST_UUID_HEADER, REQUEST_UUID)
        .withHeader(USER_ID_HEADER, USER_ID)
        .withHeader(COMPANY_ID_HEADER, COMPANY_ID)
        .withBody(subString("\"work_type\"")));
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
        .POST(HttpRequest.BodyPublishers.ofString(skillEnhanceRequestBody()))
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
    seedUser(UserType.CANDIDATE);
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
    seedUser(UserType.COMPANY);
    stubMlSkillEnhance();

    HttpResponse<String> response = postSkillEnhance();

    assertThat(response.statusCode(), is(400));
    assertThat(parse(response.body()).get("success"), is(false));
    lynqMlMock.verify(request().withPath(ML_SKILL_ENHANCE_PATH), VerificationTimes.exactly(0));
  }

  // ---------------------------------------------------------------------------
  // Job increase-seen
  // ---------------------------------------------------------------------------

  @Test
  void increaseSeenIncrementsCounterAndReturnsUpdatedValue() throws Exception {
    stubIamValidateToken();
    stubIamUserInfo();
    seedSingleJob(INITIAL_SEEN);

    HttpResponse<String> response = patchIncreaseSeen(JOB_ID);

    assertThat(response.statusCode(), is(200));
    Map<String, Object> body = parse(response.body());
    assertThat(body.get("success"), is(true));
    assertThat(((Number) body.get("data")).longValue(), is(INITIAL_SEEN + 1));
    assertThat(jobPostRepository.findById(JOB_ID).orElseThrow().getTotalSeen(),
        is(INITIAL_SEEN + 1));
  }

  @Test
  void increaseSeenTwiceAddsUpOnPersistedCounter() throws Exception {
    stubIamValidateToken();
    stubIamUserInfo();
    seedSingleJob(INITIAL_SEEN);

    patchIncreaseSeen(JOB_ID);
    HttpResponse<String> response = patchIncreaseSeen(JOB_ID);

    assertThat(response.statusCode(), is(200));
    assertThat(((Number) parse(response.body()).get("data")).longValue(), is(INITIAL_SEEN + 2));
    assertThat(jobPostRepository.findById(JOB_ID).orElseThrow().getTotalSeen(),
        is(INITIAL_SEEN + 2));
  }

  @Test
  void increaseSeenReturnsNotFoundWhenJobDoesNotExist() throws Exception {
    stubIamValidateToken();
    stubIamUserInfo();

    HttpResponse<String> response = patchIncreaseSeen(UNKNOWN_JOB_ID);

    assertThat(response.statusCode(), is(404));
    assertThat(parse(response.body()).get("success"), is(false));
  }

  @Test
  void increaseSeenReturnsUnauthorizedWhenIamRejectsToken() throws Exception {
    stubIamInvalidToken();
    seedSingleJob(INITIAL_SEEN);

    HttpResponse<String> response = patchIncreaseSeen(JOB_ID);

    assertThat(response.statusCode(), is(401));
    assertThat(jobPostRepository.findById(JOB_ID).orElseThrow().getTotalSeen(), is(INITIAL_SEEN));
  }

  // ---------------------------------------------------------------------------
  // Job apply
  // ---------------------------------------------------------------------------

  @Test
  void applyToJobPersistsApplicationAndReturnsCreated() throws Exception {
    stubIamValidateToken();
    stubIamUserInfo();
    seedUser(UserType.CANDIDATE);
    seedSingleJob(INITIAL_SEEN);

    HttpResponse<String> response = postApply(JOB_ID);

    assertThat(response.statusCode(), is(201));
    Map<String, Object> body = parse(response.body());
    assertThat(body.get("success"), is(true));

    @SuppressWarnings("unchecked")
    Map<String, Object> data = (Map<String, Object>) body.get("data");
    assertThat(data.get("jobId"), is(JOB_ID));
    assertThat(data.get("userId"), is(USER_ID));
    assertThat(data.get("applicationId"), is(notNullValue()));
    assertThat(userApplicationJobRepository.existsByJobIdAndUserId(JOB_ID, USER_ID), is(true));
  }

  @Test
  void applyToJobTwiceReturnsBadRequestAndDoesNotCreateSecondApplication() throws Exception {
    stubIamValidateToken();
    stubIamUserInfo();
    seedUser(UserType.CANDIDATE);
    seedSingleJob(INITIAL_SEEN);

    assertThat(postApply(JOB_ID).statusCode(), is(201));
    HttpResponse<String> response = postApply(JOB_ID);

    assertThat(response.statusCode(), is(400));
    assertThat(parse(response.body()).get("success"), is(false));
    assertThat(userApplicationJobRepository.count(), is(1L));
  }

  @Test
  void applyToJobReturnsNotFoundWhenJobDoesNotExist() throws Exception {
    stubIamValidateToken();
    stubIamUserInfo();
    seedUser(UserType.CANDIDATE);

    HttpResponse<String> response = postApply(UNKNOWN_JOB_ID);

    assertThat(response.statusCode(), is(404));
    assertThat(parse(response.body()).get("success"), is(false));
    assertThat(userApplicationJobRepository.count(), is(0L));
  }

  @Test
  void applyToJobReturnsBadRequestWhenUserIsNotCandidate() throws Exception {
    stubIamValidateToken();
    stubIamUserInfo();
    seedUser(UserType.COMPANY);
    seedSingleJob(INITIAL_SEEN);

    HttpResponse<String> response = postApply(JOB_ID);

    assertThat(response.statusCode(), is(400));
    assertThat(parse(response.body()).get("success"), is(false));
    assertThat(userApplicationJobRepository.count(), is(0L));
  }

  // ---------------------------------------------------------------------------
  // Job candidates
  // ---------------------------------------------------------------------------

  @Test
  @SuppressWarnings("unchecked")
  void getJobCandidatesReturnsAppliedCandidatesNewestFirst() throws Exception {
    stubIamValidateToken();
    stubIamUserInfo();
    seedJobOwnedBy(seedCompanyOwner(USER_ID), null);
    seedCandidate(CANDIDATE_A_ID, CANDIDATE_A_NAME, CANDIDATE_A_POSITION);
    seedCandidate(CANDIDATE_B_ID, CANDIDATE_B_NAME, CANDIDATE_B_POSITION);
    seedApplication(APPLICATION_OLDEST_ID, CANDIDATE_B_ID, LocalDate.now().minusDays(3));
    seedApplication(APPLICATION_NEWEST_ID, CANDIDATE_A_ID, LocalDate.now());

    HttpResponse<String> response = getCandidates(JOB_ID, null, null);

    assertThat(response.statusCode(), is(200));
    Map<String, Object> body = parse(response.body());
    assertThat(body.get("success"), is(true));

    Map<String, Object> data = (Map<String, Object>) body.get("data");
    List<Map<String, Object>> content = (List<Map<String, Object>>) data.get("content");
    assertThat(content.size(), is(2));

    Map<String, Object> newest = content.get(0);
    assertThat(newest.get("id"), is(APPLICATION_NEWEST_ID));
    assertThat(newest.get("userId"), is(CANDIDATE_A_ID));
    assertThat(newest.get("jobId"), is(JOB_ID));
    assertThat(newest.get("userFullName"), is(CANDIDATE_A_NAME));
    assertThat(newest.get("userCurrentPosition"), is(CANDIDATE_A_POSITION));
    assertThat(newest.get("userProfileImage"), is(nullValue()));
    assertThat(content.get(1).get("id"), is(APPLICATION_OLDEST_ID));
  }

  @Test
  @SuppressWarnings("unchecked")
  void getJobCandidatesDefaultsPageSizeToTen() throws Exception {
    stubIamValidateToken();
    stubIamUserInfo();
    seedJobOwnedBy(seedCompanyOwner(USER_ID), null);
    seedCandidate(CANDIDATE_A_ID, CANDIDATE_A_NAME, CANDIDATE_A_POSITION);
    seedApplication(APPLICATION_NEWEST_ID, CANDIDATE_A_ID, LocalDate.now());

    HttpResponse<String> response = getCandidates(JOB_ID, null, null);

    assertThat(response.statusCode(), is(200));
    Map<String, Object> data = (Map<String, Object>) parse(response.body()).get("data");
    assertThat(((Number) data.get("size")).intValue(), is(DEFAULT_PAGE_SIZE));
    assertThat(((Number) data.get("totalElements")).longValue(), is(1L));
  }

  @Test
  @SuppressWarnings("unchecked")
  void getJobCandidatesReturnsEmptyContentWhenNoApplications() throws Exception {
    stubIamValidateToken();
    stubIamUserInfo();
    seedJobOwnedBy(seedCompanyOwner(USER_ID), null);

    HttpResponse<String> response = getCandidates(JOB_ID, null, null);

    assertThat(response.statusCode(), is(200));
    Map<String, Object> data = (Map<String, Object>) parse(response.body()).get("data");
    List<Map<String, Object>> content = (List<Map<String, Object>>) data.get("content");
    assertThat(content.isEmpty(), is(true));
    assertThat(((Number) data.get("totalElements")).longValue(), is(0L));
  }

  @Test
  @SuppressWarnings("unchecked")
  void getJobCandidatesIncludesLynqScoreFromMatchingSkills() throws Exception {
    stubIamValidateToken();
    stubIamUserInfo();
    seedJobOwnedBy(seedCompanyOwner(USER_ID), List.of(SKILL_JAVA, SKILL_SPRING));
    seedCandidateWithSkills(CANDIDATE_A_ID, CANDIDATE_A_NAME, CANDIDATE_A_POSITION,
        List.of(SKILL_JAVA));
    seedApplication(APPLICATION_NEWEST_ID, CANDIDATE_A_ID, LocalDate.now());

    HttpResponse<String> response = getCandidates(JOB_ID, null, null);

    assertThat(response.statusCode(), is(200));
    Map<String, Object> data = (Map<String, Object>) parse(response.body()).get("data");
    List<Map<String, Object>> content = (List<Map<String, Object>>) data.get("content");
    assertThat(content.size(), is(1));
    assertThat(((Number) content.get(0).get("lynqScore")).intValue(), is(MATCHING_LYNQ_SCORE));
  }

  @Test
  void getJobCandidatesReturnsForbiddenWhenCallerIsNotTheJobOwner() throws Exception {
    stubIamValidateToken();
    stubIamUserInfo();
    seedCompanyOwner(USER_ID);
    seedJobOwnedBy(seedCompanyOwner(OTHER_USER_ID), null);
    seedCandidate(CANDIDATE_A_ID, CANDIDATE_A_NAME, CANDIDATE_A_POSITION);
    seedApplication(APPLICATION_NEWEST_ID, CANDIDATE_A_ID, LocalDate.now());

    HttpResponse<String> response = getCandidates(JOB_ID, null, null);

    assertThat(response.statusCode(), is(403));
    assertThat(parse(response.body()).get("success"), is(false));
  }

  @Test
  void getJobCandidatesReturnsNotFoundWhenJobDoesNotExist() throws Exception {
    stubIamValidateToken();
    stubIamUserInfo();
    seedCompanyOwner(USER_ID);

    HttpResponse<String> response = getCandidates(UNKNOWN_JOB_ID, null, null);

    assertThat(response.statusCode(), is(404));
    assertThat(parse(response.body()).get("success"), is(false));
  }

  // ---------------------------------------------------------------------------
  // User resumes
  // ---------------------------------------------------------------------------

  @Test
  @SuppressWarnings("unchecked")
  void getUserResumesReturnsJsonAndPdfLinkForCandidate() throws Exception {
    stubIamValidateToken();
    stubIamUserInfo();
    seedCandidateUser();
    seedResume();

    HttpResponse<String> response = getResumes();

    assertThat(response.statusCode(), is(200));
    Map<String, Object> body = parse(response.body());
    assertThat(body.get("success"), is(true));

    List<Map<String, Object>> data = (List<Map<String, Object>>) body.get("data");
    assertThat(data.size(), is(1));
    Map<String, Object> resume = data.get(0);
    assertThat(resume.get("id"), is(RESUME_ID));
    assertThat(resume.get("name"), is(RESUME_NAME));
    assertThat(resume.get("language"), is(RESUME_LANGUAGE.name()));
    assertThat(resume.get("pdfUrl"), is(notNullValue()));

    Map<String, Object> resumeJson = (Map<String, Object>) resume.get("resume");
    assertThat(resumeJson.get("summary"), is(RESUME_SUMMARY));
    assertThat(((Number) resumeJson.get("years")).intValue(), is(RESUME_YEARS));
  }

  @Test
  @SuppressWarnings("unchecked")
  void getUserResumesReturnsEmptyWhenCandidateHasNoResumes() throws Exception {
    stubIamValidateToken();
    stubIamUserInfo();
    seedCandidateUser();

    HttpResponse<String> response = getResumes();

    assertThat(response.statusCode(), is(200));
    List<Map<String, Object>> data = (List<Map<String, Object>>) parse(response.body()).get("data");
    assertThat(data.isEmpty(), is(true));
  }

  @Test
  void getUserResumesReturnsBadRequestWhenUserIsNotCandidate() throws Exception {
    stubIamValidateToken();
    stubIamUserInfo();
    seedUser(UserType.COMPANY);
    seedResume();

    HttpResponse<String> response = getResumes();

    assertThat(response.statusCode(), is(400));
    assertThat(parse(response.body()).get("success"), is(false));
  }

  @Test
  void getUserResumesReturnsNotFoundWhenUserDoesNotExist() throws Exception {
    stubIamValidateToken();
    stubIamUserInfo();

    HttpResponse<String> response = getResumes();

    assertThat(response.statusCode(), is(404));
    assertThat(parse(response.body()).get("success"), is(false));
  }

  // ---------------------------------------------------------------------------
  // Helpers for the merged endpoints
  // ---------------------------------------------------------------------------

  private void seedUser(UserType type) {
    userRepository.save(UserEntity.builder()
        .id(USER_ID)
        .type(type)
        .createdOn(LocalDate.now())
        .build());
  }

  private void seedSingleJob(Long totalSeen) {
    jobPostRepository.save(JobPostEntity.builder()
        .id(JOB_ID)
        .title(JOB_TITLE)
        .workType(JOB_WORK_TYPE)
        .jobPostSource(JOB_POST_TYPE)
        .createdOn(LocalDate.now())
        .totalSeen(totalSeen)
        .build());
  }

  private void seedSingleJobWithSkills(List<String> skills) {
    JobPostEntity job = JobPostEntity.builder()
        .id(JOB_ID)
        .title(JOB_TITLE)
        .workType(JOB_WORK_TYPE)
        .jobPostSource(JOB_POST_TYPE)
        .createdOn(LocalDate.now())
        .totalSeen(INITIAL_SEEN)
        .build();
    skills.forEach(skill -> job.getSkills().add(JobPostSkillEntity.builder()
        .id("jskill-" + skill)
        .jobPost(job)
        .skill(skill)
        .build()));
    jobPostRepository.save(job);
  }

  private UserEntity seedCompanyOwner(String id) {
    return userRepository.save(UserEntity.builder()
        .id(id)
        .type(UserType.COMPANY)
        .createdOn(LocalDate.now())
        .build());
  }

  private void seedJobOwnedBy(UserEntity owner, List<String> skills) {
    JobPostEntity job = JobPostEntity.builder()
        .id(JOB_ID)
        .title(JOB_TITLE)
        .workType(JOB_WORK_TYPE)
        .jobPostSource(JOB_POST_TYPE)
        .createdOn(LocalDate.now())
        .totalSeen(INITIAL_SEEN)
        .createdByUser(owner)
        .build();
    if (skills != null) {
      skills.forEach(skill -> job.getSkills().add(JobPostSkillEntity.builder()
          .id("jskill-" + skill)
          .jobPost(job)
          .skill(skill)
          .build()));
    }
    jobPostRepository.save(job);
  }

  private void seedCandidate(String userId, String fullName, String position) {
    userRepository.save(UserEntity.builder()
        .id(userId)
        .type(UserType.CANDIDATE)
        .fullName(fullName)
        .currentPosition(position)
        .createdOn(LocalDate.now())
        .build());
  }

  private void seedCandidateWithSkills(String userId, String fullName, String position,
      List<String> skills) {
    UserEntity user = UserEntity.builder()
        .id(userId)
        .type(UserType.CANDIDATE)
        .fullName(fullName)
        .currentPosition(position)
        .createdOn(LocalDate.now())
        .build();
    skills.forEach(skill -> user.getSkills().add(UserSkillsEntity.builder()
        .id("uskill-" + skill)
        .user(user)
        .skill(skill)
        .build()));
    userRepository.save(user);
  }

  private void seedApplication(String applicationId, String userId, LocalDate appliedOn) {
    UserEntity user = userRepository.findById(userId).orElseThrow();
    JobPostEntity job = jobPostRepository.findById(JOB_ID).orElseThrow();
    userApplicationJobRepository.save(UserApplicationJobEntity.builder()
        .id(applicationId)
        .jobPost(job)
        .user(user)
        .appliedOn(appliedOn)
        .build());
  }

  private void seedResume() {
    UserEntity user = userRepository.findById(USER_ID).orElseThrow();
    userResumeRepository.save(UserResumeEntity.builder()
        .id(RESUME_ID)
        .name(RESUME_NAME)
        .language(RESUME_LANGUAGE)
        .createdOn(LocalDate.now())
        .resume(RESUME_JSON)
        .storagePath(RESUME_STORAGE_PATH)
        .user(user)
        .build());
  }

  private HttpResponse<String> postSkillEnhance() throws Exception {
    HttpRequest httpRequest = HttpRequest.newBuilder()
        .uri(URI.create(skillEnhanceUrl()))
        .header(CONTENT_TYPE_HEADER, APPLICATION_JSON)
        .header(AUTHORIZATION_HEADER, BEARER_TOKEN)
        .header(REQUEST_UUID_HEADER, REQUEST_UUID)
        .POST(HttpRequest.BodyPublishers.ofString(skillEnhanceRequestBody()))
        .build();
    return httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
  }

  private HttpResponse<String> patchIncreaseSeen(String jobId) throws Exception {
    HttpRequest httpRequest = HttpRequest.newBuilder()
        .uri(URI.create(jobSubResourceUrl(jobId, "increase-seen")))
        .header(CONTENT_TYPE_HEADER, APPLICATION_JSON)
        .header(AUTHORIZATION_HEADER, BEARER_TOKEN)
        .header(REQUEST_UUID_HEADER, REQUEST_UUID)
        .method("PATCH", HttpRequest.BodyPublishers.noBody())
        .build();
    return httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
  }

  private HttpResponse<String> postApply(String jobId) throws Exception {
    HttpRequest httpRequest = HttpRequest.newBuilder()
        .uri(URI.create(jobSubResourceUrl(jobId, "apply")))
        .header(CONTENT_TYPE_HEADER, APPLICATION_JSON)
        .header(AUTHORIZATION_HEADER, BEARER_TOKEN)
        .header(REQUEST_UUID_HEADER, REQUEST_UUID)
        .POST(HttpRequest.BodyPublishers.noBody())
        .build();
    return httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
  }

  private HttpResponse<String> getCandidates(String jobId, Integer page, Integer pageSize)
      throws Exception {
    StringBuilder url = new StringBuilder(jobSubResourceUrl(jobId, "candidates"));
    if (page != null || pageSize != null) {
      url.append("?");
      if (page != null) {
        url.append("page=").append(page).append("&");
      }
      if (pageSize != null) {
        url.append("pageSize=").append(pageSize);
      }
    }
    HttpRequest httpRequest = HttpRequest.newBuilder()
        .uri(URI.create(url.toString()))
        .header(AUTHORIZATION_HEADER, BEARER_TOKEN)
        .header(REQUEST_UUID_HEADER, REQUEST_UUID)
        .GET()
        .build();
    return httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
  }

  private HttpResponse<String> getResumes() throws Exception {
    HttpRequest httpRequest = HttpRequest.newBuilder()
        .uri(URI.create("http://localhost:" + port + CONTEXT_PATH + RESUME_PATH))
        .header(AUTHORIZATION_HEADER, BEARER_TOKEN)
        .header(REQUEST_UUID_HEADER, REQUEST_UUID)
        .GET()
        .build();
    return httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
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

  private String skillEnhanceRequestBody() {
    SkillEnhanceRequest request = new SkillEnhanceRequest();
    request.setTitle(JOB_TITLE);
    request.setDescription(JOB_DESCRIPTION);
    request.setWorkType(JOB_WORK_TYPE);
    return objectMapper.writeValueAsString(request);
  }

  private String skillEnhanceUrl() {
    return "http://localhost:" + port + CONTEXT_PATH + SKILL_ENHANCE_PROXY_PATH;
  }

  private String jobSubResourceUrl(String jobId, String subResource) {
    return "http://localhost:" + port + CONTEXT_PATH + CREATE_JOB_PATH + "/" + jobId + "/"
        + subResource;
  }
}