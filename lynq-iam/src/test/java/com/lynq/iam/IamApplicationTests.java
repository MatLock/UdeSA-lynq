package com.lynq.iam;

import tools.jackson.databind.JsonNode;
import com.lynq.iam.controller.request.CreateUserRequest;
import com.lynq.iam.controller.request.EmailUserLogin;
import com.lynq.iam.controller.request.UserUpdatePasswordRequest;
import com.lynq.iam.controller.request.UsernameLogin;
import com.lynq.iam.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;

import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.Matchers.is;

class IamApplicationTests extends AbstractE2ETest {

	private static final String CONTEXT_PATH = "/lynq-iam";
	private static final String AUTH_PATH = CONTEXT_PATH + "/auth";
	private static final String REGISTER_ENDPOINT = AUTH_PATH + "/register";
	private static final String LOGIN_USERNAME_ENDPOINT = AUTH_PATH + "/login/username";
	private static final String LOGIN_EMAIL_ENDPOINT = AUTH_PATH + "/login/email";
	private static final String UPDATE_PASSWORD_ENDPOINT = AUTH_PATH + "/update-password";
	private static final String VALIDATE_ENDPOINT = AUTH_PATH + "/validate";
	private static final String REFRESH_ENDPOINT = AUTH_PATH + "/refresh";
	private static final String USERINFO_ENDPOINT = AUTH_PATH + "/user-info";

	private static final String REQUEST_UUID_HEADER = "lynq-request-uuid";
	private static final String AUTHORIZATION_HEADER = "Authorization";
	private static final String BEARER_PREFIX = "Bearer ";

	private static final String VALID_PASSWORD = "P@ssw0rd123";
	private static final String UPDATED_PASSWORD = "N3wStr0ngPass!";
	private static final String INVALID_PASSWORD = "wrong-password-1";
	private static final String MISSING_USERNAME = "ghostuser";
	private static final String MISSING_EMAIL = "ghost@example.com";
	private static final String INVALID_REFRESH_TOKEN = "definitely-not-a-real-refresh-token";
	private static final String MALFORMED_ACCESS_TOKEN = "not.a.valid.jwt";

	@LocalServerPort
	private int port;

	@Autowired
	private TestRestTemplate restTemplate;

	@Autowired
	private UserRepository userRepository;

	private String baseUrl;

	@BeforeEach
	void setUp() {
		baseUrl = "http://localhost:" + port;
		userRepository.deleteAll();
	}

	@Test
	void registerEndpointReturnsCreatedStatusAndIssuesTokensForNewUser() {
		CreateUserRequest request = buildCreateUserRequest(uniqueUsername(), uniqueEmail(), VALID_PASSWORD);

		ResponseEntity<JsonNode> response = postJson(REGISTER_ENDPOINT, request);

		assertThat(response.getStatusCode(), is(HttpStatus.CREATED));
		JsonNode data = response.getBody().get("data");
		assertThat(data.get("id").asText(), is(notNullValue()));
		assertThat(data.get("username").asText(), is(request.getUsername()));
		assertThat(data.get("email").asText(), is(request.getEmail()));
		assertThat(data.get("accessToken").asText().isBlank(), is(false));
		assertThat(data.get("refreshToken").asText().isBlank(), is(false));
	}

	@Test
	void registerEndpointReturnsBadRequestWhenRequestBodyFailsValidation() {
		CreateUserRequest invalidRequest = buildCreateUserRequest("ab", "not-an-email", "short");

		ResponseEntity<JsonNode> response = postJson(REGISTER_ENDPOINT, invalidRequest);

		assertThat(response.getStatusCode(), is(HttpStatus.BAD_REQUEST));
		assertThat(response.getBody().get("success").asBoolean(), is(false));
	}

	@Test
	void registerEndpointReturnsConflictWhenUsernameAlreadyExists() {
		CreateUserRequest first = buildCreateUserRequest(uniqueUsername(), uniqueEmail(), VALID_PASSWORD);
		postJson(REGISTER_ENDPOINT, first);
		CreateUserRequest duplicate = buildCreateUserRequest(first.getUsername(), uniqueEmail(), VALID_PASSWORD);

		ResponseEntity<JsonNode> response = postJson(REGISTER_ENDPOINT, duplicate);

		assertThat(response.getStatusCode(), is(HttpStatus.CONFLICT));
	}

	@Test
	void registerEndpointReturnsForbiddenWhenRequestUuidHeaderIsMissing() {
		CreateUserRequest request = buildCreateUserRequest(uniqueUsername(), uniqueEmail(), VALID_PASSWORD);
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);

		ResponseEntity<JsonNode> response = restTemplate.exchange(
				baseUrl + REGISTER_ENDPOINT,
				HttpMethod.POST,
				new HttpEntity<>(request, headers),
				JsonNode.class);

		assertThat(response.getStatusCode(), is(HttpStatus.FORBIDDEN));
	}

	@Test
	void loginByUsernameEndpointReturnsOkAndTokensWhenCredentialsAreValid() {
		String username = uniqueUsername();
		String email = uniqueEmail();
		registerUser(username, email, VALID_PASSWORD);
		UsernameLogin loginRequest = new UsernameLogin(username, VALID_PASSWORD);

		ResponseEntity<JsonNode> response = postJson(LOGIN_USERNAME_ENDPOINT, loginRequest);

		assertThat(response.getStatusCode(), is(HttpStatus.OK));
		JsonNode data = response.getBody().get("data");
		assertThat(data.get("username").asText(), is(username));
		assertThat(data.get("email").asText(), is(email));
		assertThat(data.get("accessToken").asText().isBlank(), is(false));
		assertThat(data.get("refreshToken").asText().isBlank(), is(false));
	}

	@Test
	void loginByUsernameEndpointReturnsForbiddenWhenPasswordDoesNotMatch() {
		String username = uniqueUsername();
		registerUser(username, uniqueEmail(), VALID_PASSWORD);
		UsernameLogin loginRequest = new UsernameLogin(username, INVALID_PASSWORD);

		ResponseEntity<JsonNode> response = postJson(LOGIN_USERNAME_ENDPOINT, loginRequest);

		assertThat(response.getStatusCode(), is(HttpStatus.FORBIDDEN));
	}

	@Test
	void loginByUsernameEndpointReturnsForbiddenWhenUserDoesNotExist() {
		UsernameLogin loginRequest = new UsernameLogin(MISSING_USERNAME, VALID_PASSWORD);

		ResponseEntity<JsonNode> response = postJson(LOGIN_USERNAME_ENDPOINT, loginRequest);

		assertThat(response.getStatusCode(), is(HttpStatus.FORBIDDEN));
	}

	@Test
	void loginByEmailEndpointReturnsOkAndTokensWhenCredentialsAreValid() {
		String username = uniqueUsername();
		String email = uniqueEmail();
		registerUser(username, email, VALID_PASSWORD);
		EmailUserLogin loginRequest = new EmailUserLogin(email, VALID_PASSWORD);

		ResponseEntity<JsonNode> response = postJson(LOGIN_EMAIL_ENDPOINT, loginRequest);

		assertThat(response.getStatusCode(), is(HttpStatus.OK));
		JsonNode data = response.getBody().get("data");
		assertThat(data.get("username").asText(), is(username));
		assertThat(data.get("email").asText(), is(email));
	}

	@Test
	void loginByEmailEndpointReturnsForbiddenWhenPasswordDoesNotMatch() {
		String email = uniqueEmail();
		registerUser(uniqueUsername(), email, VALID_PASSWORD);
		EmailUserLogin loginRequest = new EmailUserLogin(email, INVALID_PASSWORD);

		ResponseEntity<JsonNode> response = postJson(LOGIN_EMAIL_ENDPOINT, loginRequest);

		assertThat(response.getStatusCode(), is(HttpStatus.FORBIDDEN));
	}

	@Test
	void loginByEmailEndpointReturnsForbiddenWhenUserDoesNotExist() {
		EmailUserLogin loginRequest = new EmailUserLogin(MISSING_EMAIL, VALID_PASSWORD);

		ResponseEntity<JsonNode> response = postJson(LOGIN_EMAIL_ENDPOINT, loginRequest);

		assertThat(response.getStatusCode(), is(HttpStatus.FORBIDDEN));
	}

	@Test
	void updatePasswordEndpointReturnsOkAndReissuesTokensWhenAccessTokenIsValid() {
		String username = uniqueUsername();
		String email = uniqueEmail();
		JsonNode registered = registerUser(username, email, VALID_PASSWORD);
		String originalAccessToken = registered.get("accessToken").asText();
		UserUpdatePasswordRequest updateRequest = buildUpdatePasswordRequest(UPDATED_PASSWORD);

		HttpHeaders headers = jsonHeadersWithUuid();
		headers.set(AUTHORIZATION_HEADER, BEARER_PREFIX + originalAccessToken);
		ResponseEntity<JsonNode> response = restTemplate.exchange(
				baseUrl + UPDATE_PASSWORD_ENDPOINT,
				HttpMethod.PATCH,
				new HttpEntity<>(updateRequest, headers),
				JsonNode.class);

		assertThat(response.getStatusCode(), is(HttpStatus.OK));
		JsonNode data = response.getBody().get("data");
		assertThat(data.get("username").asText(), is(username));
		assertThat(data.get("email").asText(), is(email));
		assertThat(data.get("accessToken").asText(), is(not(originalAccessToken)));
		assertThat(data.get("refreshToken").asText().isBlank(), is(false));
	}

	@Test
	void validateEndpointReturnsOkWithTrueDataWhenAccessTokenIsValid() {
		JsonNode registered = registerUser(uniqueUsername(), uniqueEmail(), VALID_PASSWORD);
		String accessToken = registered.get("accessToken").asText();

		ResponseEntity<JsonNode> response = getWithAuth(VALIDATE_ENDPOINT, BEARER_PREFIX + accessToken);

		assertThat(response.getStatusCode(), is(HttpStatus.OK));
		assertThat(response.getBody().get("success").asBoolean(), is(true));
		assertThat(response.getBody().get("data").asBoolean(), is(true));
	}

	@Test
	void validateEndpointReturnsOkWithFalseDataWhenAccessTokenIsMalformed() {
		ResponseEntity<JsonNode> response = getWithAuth(VALIDATE_ENDPOINT, BEARER_PREFIX + MALFORMED_ACCESS_TOKEN);

		assertThat(response.getStatusCode(), is(HttpStatus.OK));
		assertThat(response.getBody().get("data").asBoolean(), is(false));
	}

	@Test
	void refreshEndpointReturnsOkAndNewAccessTokenWhenRefreshTokenIsValid() {
		JsonNode registered = registerUser(uniqueUsername(), uniqueEmail(), VALID_PASSWORD);
		String refreshToken = registered.get("refreshToken").asText();

		HttpHeaders headers = jsonHeadersWithUuid();
		headers.set(AUTHORIZATION_HEADER, BEARER_PREFIX + refreshToken);
		ResponseEntity<JsonNode> response = restTemplate.exchange(
				baseUrl + REFRESH_ENDPOINT,
				HttpMethod.POST,
				new HttpEntity<>(null, headers),
				JsonNode.class);

		assertThat(response.getStatusCode(), is(HttpStatus.OK));
		JsonNode data = response.getBody().get("data");
		assertThat(data.get("accessToken").asText().isBlank(), is(false));
	}

	@Test
	void refreshEndpointReturnsForbiddenWhenRefreshTokenIsUnknown() {
		HttpHeaders headers = jsonHeadersWithUuid();
		headers.set(AUTHORIZATION_HEADER, BEARER_PREFIX + INVALID_REFRESH_TOKEN);

		ResponseEntity<JsonNode> response = restTemplate.exchange(
				baseUrl + REFRESH_ENDPOINT,
				HttpMethod.POST,
				new HttpEntity<>(null, headers),
				JsonNode.class);

		assertThat(response.getStatusCode(), is(HttpStatus.FORBIDDEN));
	}

	@Test
	void userInfoEndpointReturnsOkWithUserClaimsExtractedFromAccessToken() {
		String username = uniqueUsername();
		String email = uniqueEmail();
		JsonNode registered = registerUser(username, email, VALID_PASSWORD);
		String accessToken = registered.get("accessToken").asText();

		ResponseEntity<JsonNode> response = getWithAuth(USERINFO_ENDPOINT, BEARER_PREFIX + accessToken);

		assertThat(response.getStatusCode(), is(HttpStatus.OK));
		JsonNode data = response.getBody().get("data");
		assertThat(data.get("id").asText(), is(notNullValue()));
		assertThat(data.get("username").asText(), is(username));
		assertThat(data.get("email").asText(), is(email));
	}

	private CreateUserRequest buildCreateUserRequest(String username, String email, String password) {
		return CreateUserRequest.builder()
				.username(username)
				.email(email)
				.password(password)
				.build();
	}

	private UserUpdatePasswordRequest buildUpdatePasswordRequest(String newPassword) {
		UserUpdatePasswordRequest request = new UserUpdatePasswordRequest();
		request.setNewPassword(newPassword);
		return request;
	}

	private JsonNode registerUser(String username, String email, String password) {
		ResponseEntity<JsonNode> response = postJson(REGISTER_ENDPOINT, buildCreateUserRequest(username, email, password));
		return response.getBody().get("data");
	}

	private ResponseEntity<JsonNode> postJson(String endpoint, Object body) {
		return restTemplate.exchange(
				baseUrl + endpoint,
				HttpMethod.POST,
				new HttpEntity<>(body, jsonHeadersWithUuid()),
				JsonNode.class);
	}

	private ResponseEntity<JsonNode> getWithAuth(String endpoint, String authorizationHeaderValue) {
		HttpHeaders headers = jsonHeadersWithUuid();
		headers.set(AUTHORIZATION_HEADER, authorizationHeaderValue);
		return restTemplate.exchange(
				baseUrl + endpoint,
				HttpMethod.GET,
				new HttpEntity<>(null, headers),
				JsonNode.class);
	}

	private HttpHeaders jsonHeadersWithUuid() {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		headers.set(REQUEST_UUID_HEADER, UUID.randomUUID().toString());
		return headers;
	}

	private String uniqueUsername() {
		return ("u" + UUID.randomUUID().toString().replace("-", "")).substring(0, 20);
	}

	private String uniqueEmail() {
		return "e2e-" + UUID.randomUUID() + "@example.com";
	}

}
