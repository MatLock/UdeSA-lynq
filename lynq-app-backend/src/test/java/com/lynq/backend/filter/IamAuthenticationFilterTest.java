package com.lynq.backend.filter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.lynq.backend.client.LynqIamClient;
import com.lynq.backend.client.response.UserInfoResponse;
import com.lynq.backend.controller.response.GlobalRestResponse;
import com.lynq.backend.security.LynqUserPrincipal;
import feign.FeignException;
import feign.Request;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collections;
import java.util.HashMap;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IamAuthenticationFilterTest {

  private static final String AUTHORIZATION_HEADER = "Authorization";
  private static final String REQUEST_UUID_HEADER = "lynq-request-uuid";
  private static final String VALID_AUTH_HEADER_VALUE = "Bearer eyJhbGciOiJIUzI1NiJ9.access.token";
  private static final String REQUEST_UUID_VALUE = "550e8400-e29b-41d4-a716-446655440000";

  private static final String USER_ID = "550e8400-e29b-41d4-a716-446655440000";
  private static final String USERNAME = "johndoe";
  private static final String EMAIL = "johndoe@example.com";

  private static final String EXPECTED_IAM_UNAVAILABLE_REASON = "Authentication service is unavailable";
  private static final int UNAUTHORIZED = HttpStatus.UNAUTHORIZED.value();
  private static final int SERVICE_UNAVAILABLE = HttpStatus.SERVICE_UNAVAILABLE.value();

  @Mock
  private LynqIamClient lynqIamClient;
  @Mock
  private HttpServletRequest request;

  @Mock
  private HttpServletResponse response;

  @Mock
  private FilterChain filterChain;

  @Mock
  private PrintWriter responseWriter;

  private IamAuthenticationFilter filter;
  private ObjectMapper objectMapper;


  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper();
    objectMapper.registerModule(new JavaTimeModule());
    filter = new IamAuthenticationFilter(lynqIamClient, objectMapper);
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void writesUnauthorizedWhenTokenValidationReturnsFalse() throws Exception {
    stubHeaders();
    when(lynqIamClient.validateToken(VALID_AUTH_HEADER_VALUE, REQUEST_UUID_VALUE))
        .thenReturn(new GlobalRestResponse<>(true, Boolean.FALSE));
    when(response.getWriter()).thenReturn(responseWriter);

    filter.doFilterInternal(request, response, filterChain);

    verify(response).setStatus(UNAUTHORIZED);
    verify(filterChain, never()).doFilter(any(), any());
    verify(lynqIamClient, never()).getUserInfo(any(), any());
  }

  @Test
  void writesUnauthorizedWhenUserInfoIsMissing() throws Exception {
    stubHeaders();
    when(lynqIamClient.validateToken(VALID_AUTH_HEADER_VALUE, REQUEST_UUID_VALUE))
        .thenReturn(new GlobalRestResponse<>(true, Boolean.TRUE));
    when(lynqIamClient.getUserInfo(VALID_AUTH_HEADER_VALUE, REQUEST_UUID_VALUE))
        .thenReturn(new GlobalRestResponse<>(true, null));
    when(response.getWriter()).thenReturn(responseWriter);

    filter.doFilterInternal(request, response, filterChain);

    verify(response).setStatus(UNAUTHORIZED);
    verify(filterChain, never()).doFilter(any(), any());
  }

  @Test
  void loadsUserIntoSecurityContextAndDelegatesWhenTokenIsValid() throws Exception {
    stubHeaders();
    when(lynqIamClient.validateToken(VALID_AUTH_HEADER_VALUE, REQUEST_UUID_VALUE))
        .thenReturn(new GlobalRestResponse<>(true, Boolean.TRUE));
    when(lynqIamClient.getUserInfo(VALID_AUTH_HEADER_VALUE, REQUEST_UUID_VALUE))
        .thenReturn(new GlobalRestResponse<>(true, new UserInfoResponse(USER_ID, USERNAME, EMAIL)));

    LynqUserPrincipal[] principalDuringChain = new LynqUserPrincipal[1];
    doAnswer(invocation -> {
      Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
      principalDuringChain[0] = (LynqUserPrincipal) authentication.getPrincipal();
      return null;
    }).when(filterChain).doFilter(request, response);

    filter.doFilterInternal(request, response, filterChain);

    verify(filterChain).doFilter(request, response);
    assertThat(principalDuringChain[0], is(instanceOf(LynqUserPrincipal.class)));
    assertThat(principalDuringChain[0].getId(), is(USER_ID));
    assertThat(principalDuringChain[0].getUsername(), is(USERNAME));
    assertThat(principalDuringChain[0].getEmail(), is(EMAIL));
    // context is cleared once the request completes
    assertThat(SecurityContextHolder.getContext().getAuthentication(), is(nullValue()));
  }

  @Test
  void writesUnauthorizedWhenIamRejectsToken() throws Exception {
    stubHeaders();
    when(lynqIamClient.validateToken(VALID_AUTH_HEADER_VALUE, REQUEST_UUID_VALUE))
        .thenThrow(unauthorizedFeignException());
    when(response.getWriter()).thenReturn(responseWriter);

    filter.doFilterInternal(request, response, filterChain);

    verify(response).setStatus(UNAUTHORIZED);
    verify(filterChain, never()).doFilter(any(), any());
  }

  @Test
  void writesServiceUnavailableWhenIamCallFails() throws Exception {
    stubHeaders();
    StringWriter responseBody = new StringWriter();
    when(lynqIamClient.validateToken(VALID_AUTH_HEADER_VALUE, REQUEST_UUID_VALUE))
        .thenThrow(serviceUnavailableFeignException());
    when(response.getWriter()).thenReturn(new PrintWriter(responseBody));

    filter.doFilterInternal(request, response, filterChain);

    verify(response).setStatus(SERVICE_UNAVAILABLE);
    verify(filterChain, never()).doFilter(any(), any());
    JsonNode body = objectMapper.readTree(responseBody.toString());
    assertThat(body.get("reason").asText(), is(EXPECTED_IAM_UNAVAILABLE_REASON));
  }

  private void stubHeaders() {
    when(request.getHeader(AUTHORIZATION_HEADER)).thenReturn(VALID_AUTH_HEADER_VALUE);
    when(request.getHeader(REQUEST_UUID_HEADER)).thenReturn(REQUEST_UUID_VALUE);
  }

  private static FeignException unauthorizedFeignException() {
    return FeignException.errorStatus("LynqIamClient#validateToken", dummyResponse(HttpStatus.UNAUTHORIZED.value()));
  }

  private static FeignException serviceUnavailableFeignException() {
    return FeignException.errorStatus("LynqIamClient#validateToken", dummyResponse(HttpStatus.SERVICE_UNAVAILABLE.value()));
  }

  private static feign.Response dummyResponse(int status) {
    Request request = Request.create(
        Request.HttpMethod.GET, "http://localhost/lynq-iam/auth/validate",
        Collections.emptyMap(), Request.Body.empty(), null);
    return feign.Response.builder()
        .status(status)
        .reason("error")
        .request(request)
        .headers(new HashMap<>())
        .body(new byte[0])
        .build();
  }
}
