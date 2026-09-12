package com.lynq.bff.service;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lynq.bff.client.LynqIamAuthClient;
import com.lynq.bff.exceptions.BadGatewayException;
import com.lynq.bff.exceptions.MethodNotAllowedException;
import com.lynq.bff.filter.JwtSignatureFilter;
import feign.Request;
import feign.RetryableException;
import feign.Response;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

@ExtendWith(MockitoExtension.class)
class IamAuthProxyServiceTest {

  private static final String LOGIN_PATH = "login/email";
  private static final String CHECK_USERNAME_PATH = "check-username";
  private static final String REFRESH_PATH = "refresh";
  private static final String UPDATE_PASSWORD_PATH = "update-password";
  private static final String OPAQUE_REFRESH_TOKEN = "8f14e45f-ceea-467a-9ae4-9b3f4a1c2d3e";
  private static final String SPOOFED_USER_ID = "99999999-9999-9999-9999-999999999999";
  private static final String RESPONSE_BODY = """
      {"success": true}""";

  @Mock
  private LynqIamAuthClient lynqIamAuthClient;

  private IamAuthProxyService iamAuthProxyService;

  @BeforeEach
  void setUp() {
    iamAuthProxyService = new IamAuthProxyService();
  }

  @Test
  void relaysTheStatusAndBodyLynqIamReturned() throws Exception {
    when(lynqIamAuthClient.post(eq(LOGIN_PATH), any(), any(), any()))
        .thenReturn(iamResponse(403, Map.of("content-type", List.of("application/json"))));

    ResponseEntity<byte[]> response =
        iamAuthProxyService.relay(lynqIamAuthClient, LOGIN_PATH, loginRequest());

    assertThat(response.getStatusCode().value(), is(403));
    assertThat(new String(response.getBody(), StandardCharsets.UTF_8), is(RESPONSE_BODY));
    assertThat(response.getHeaders().getFirst("content-type"), is("application/json"));
  }

  @Test
  void passesTheCredentialsInTheBodyOnAsRawBytes() throws Exception {
    String body = """
        {"email": "jane@lynq.com", "password": "s3cr3tpass"}""";
    MockHttpServletRequest request = loginRequest();
    request.setContent(body.getBytes(StandardCharsets.UTF_8));
    when(lynqIamAuthClient.post(eq(LOGIN_PATH), any(), any(), any()))
        .thenReturn(iamResponse(200, Map.of()));

    iamAuthProxyService.relay(lynqIamAuthClient, LOGIN_PATH, request);

    ArgumentCaptor<byte[]> bodyCaptor = ArgumentCaptor.forClass(byte[].class);
    verify(lynqIamAuthClient).post(eq(LOGIN_PATH), any(), any(), bodyCaptor.capture());
    assertThat(new String(bodyCaptor.getValue(), StandardCharsets.UTF_8), is(body));
  }

  @Test
  void forwardsTheAvailabilityCheckQueryString() throws Exception {
    MockHttpServletRequest request =
        new MockHttpServletRequest("GET", "/auth/" + CHECK_USERNAME_PATH);
    request.setContent(new byte[0]);
    request.setParameter("username", "janedoe");
    when(lynqIamAuthClient.get(eq(CHECK_USERNAME_PATH), any(), any()))
        .thenReturn(iamResponse(200, Map.of()));

    iamAuthProxyService.relay(lynqIamAuthClient, CHECK_USERNAME_PATH, request);

    ArgumentCaptor<Map<String, Collection<String>>> captor = queryCaptor();
    verify(lynqIamAuthClient).get(eq(CHECK_USERNAME_PATH), captor.capture(), any());
    assertThat(captor.getValue(), hasEntry(is("username"), contains("janedoe")));
  }

  @Test
  void forwardsTheOpaqueRefreshCredentialUntouched() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/" + REFRESH_PATH);
    request.setContent(new byte[0]);
    request.addHeader("Authorization", "Bearer " + OPAQUE_REFRESH_TOKEN);
    when(lynqIamAuthClient.post(eq(REFRESH_PATH), any(), any(), any()))
        .thenReturn(iamResponse(200, Map.of()));

    iamAuthProxyService.relay(lynqIamAuthClient, REFRESH_PATH, request);

    assertThat(capturedPostHeaders(REFRESH_PATH),
        hasEntry(is("Authorization"), contains("Bearer " + OPAQUE_REFRESH_TOKEN)));
  }

  @Test
  void relaysThePasswordUpdateAsAPatch() throws Exception {
    MockHttpServletRequest request =
        new MockHttpServletRequest("PATCH", "/auth/" + UPDATE_PASSWORD_PATH);
    request.setContent("{\"newPassword\": \"an0th3rpass\"}".getBytes(StandardCharsets.UTF_8));
    when(lynqIamAuthClient.patch(eq(UPDATE_PASSWORD_PATH), any(), any(), any()))
        .thenReturn(iamResponse(200, Map.of()));

    ResponseEntity<byte[]> response =
        iamAuthProxyService.relay(lynqIamAuthClient, UPDATE_PASSWORD_PATH, request);

    assertThat(response.getStatusCode().value(), is(200));
    verify(lynqIamAuthClient).patch(eq(UPDATE_PASSWORD_PATH), any(), any(), any());
  }

  @Test
  void addsNoUserIdHeaderOfItsOwnBecauseLynqIamReadsTheCredentialItself() throws Exception {
    MockHttpServletRequest request = loginRequest();
    request.setAttribute(JwtSignatureFilter.VERIFIED_USER_ID,
        "11111111-1111-1111-1111-111111111111");
    when(lynqIamAuthClient.post(eq(LOGIN_PATH), any(), any(), any()))
        .thenReturn(iamResponse(200, Map.of()));

    iamAuthProxyService.relay(lynqIamAuthClient, LOGIN_PATH, request);

    assertThat(capturedPostHeaders(LOGIN_PATH).containsKey("user-id"), is(false));
  }

  @Test
  void dropsAClientSuppliedUserIdHeader() throws Exception {
    MockHttpServletRequest request = loginRequest();
    request.addHeader("user-id", SPOOFED_USER_ID);
    when(lynqIamAuthClient.post(eq(LOGIN_PATH), any(), any(), any()))
        .thenReturn(iamResponse(200, Map.of()));

    iamAuthProxyService.relay(lynqIamAuthClient, LOGIN_PATH, request);

    assertThat(capturedPostHeaders(LOGIN_PATH).containsKey("user-id"), is(false));
  }

  @Test
  void dropsTheRequestUuidLynqIamEchoedSoItIsNotSentTwice() throws Exception {
    when(lynqIamAuthClient.post(eq(LOGIN_PATH), any(), any(), any())).thenReturn(iamResponse(200,
        Map.of("lynq-request-uuid", List.of("550e8400-e29b-41d4-a716-446655440000"))));

    ResponseEntity<byte[]> response =
        iamAuthProxyService.relay(lynqIamAuthClient, LOGIN_PATH, loginRequest());

    assertThat(response.getHeaders().containsHeader("lynq-request-uuid"), is(false));
  }

  @Test
  void leavesABodilessAnswerWithoutABody() throws Exception {
    Response noContent = Response.builder()
        .status(204)
        .request(dummyRequest())
        .headers(Map.of())
        .build();
    when(lynqIamAuthClient.post(eq(LOGIN_PATH), any(), any(), any())).thenReturn(noContent);

    ResponseEntity<byte[]> response =
        iamAuthProxyService.relay(lynqIamAuthClient, LOGIN_PATH, loginRequest());

    assertThat(response.getStatusCode().value(), is(204));
    assertThat(response.getBody(), is(nullValue()));
  }

  @Test
  void turnsAnUnreachableLynqIamIntoABadGateway() {
    when(lynqIamAuthClient.post(eq(LOGIN_PATH), any(), any(), any()))
        .thenThrow(connectionFailure());

    MockHttpServletRequest request = loginRequest();

    assertThrows(BadGatewayException.class,
        () -> iamAuthProxyService.relay(lynqIamAuthClient, LOGIN_PATH, request));
  }

  @Test
  void rejectsAVerbItDoesNotRelay() {
    MockHttpServletRequest request = loginRequest();
    request.setMethod("DELETE");

    assertThrows(MethodNotAllowedException.class,
        () -> iamAuthProxyService.relay(lynqIamAuthClient, LOGIN_PATH, request));
  }

  private Map<String, Collection<String>> capturedPostHeaders(String path) {
    ArgumentCaptor<Map<String, Collection<String>>> captor = queryCaptor();
    verify(lynqIamAuthClient).post(eq(path), any(), captor.capture(), any());
    return captor.getValue();
  }

  @SuppressWarnings("unchecked")
  private static ArgumentCaptor<Map<String, Collection<String>>> queryCaptor() {
    return ArgumentCaptor.forClass(Map.class);
  }

  private static MockHttpServletRequest loginRequest() {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/auth/" + LOGIN_PATH);
    request.setContent(new byte[0]);
    return request;
  }

  private static Response iamResponse(int status, Map<String, Collection<String>> headers) {
    return Response.builder()
        .status(status)
        .request(dummyRequest())
        .headers(headers)
        .body(RESPONSE_BODY, StandardCharsets.UTF_8)
        .build();
  }

  private static RetryableException connectionFailure() {
    return new RetryableException(-1, "Connection refused", Request.HttpMethod.POST,
        new IOException("refused"), (Long) null, dummyRequest());
  }

  private static Request dummyRequest() {
    return Request.create(Request.HttpMethod.POST, "http://localhost/lynq-iam/auth/login/email",
        Collections.emptyMap(), Request.Body.empty(), null);
  }
}
