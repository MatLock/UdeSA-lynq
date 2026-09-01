package com.lynq.bff.filter;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynq.bff.security.JwtSignatureVerifier;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

@ExtendWith(MockitoExtension.class)
class JwtSignatureFilterTest {

  private static final String AUTHORIZATION_HEADER = "Authorization";
  private static final String RAW_TOKEN = "eyJhbGciOiJIUzI1NiJ9.access.token";
  private static final String USER_ID = "11111111-1111-1111-1111-111111111111";
  private static final String BEARER_HEADER_VALUE = "Bearer " + RAW_TOKEN;
  private static final String EXPECTED_INVALID_TOKEN_REASON = "Invalid or expired access token";
  private static final int EXPECTED_UNAUTHORIZED_STATUS_CODE = HttpStatus.UNAUTHORIZED.value();
  private static final String EXPECTED_CONTENT_TYPE = MediaType.APPLICATION_JSON_VALUE;
  private static final boolean EXPECTED_ERROR_SUCCESS_FLAG = false;

  @Mock
  private JwtSignatureVerifier jwtSignatureVerifier;

  @Mock
  private HttpServletRequest request;

  @Mock
  private HttpServletResponse response;

  @Mock
  private FilterChain filterChain;

  @Mock
  private PrintWriter responseWriter;

  private JwtSignatureFilter filter;
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper();
    filter = new JwtSignatureFilter(jwtSignatureVerifier, objectMapper);
  }

  @Test
  void stripsTheBearerPrefixBeforeVerifyingTheSignature() throws Exception {
    when(request.getHeader(AUTHORIZATION_HEADER)).thenReturn(BEARER_HEADER_VALUE);
    when(jwtSignatureVerifier.verifiedSubject(RAW_TOKEN)).thenReturn(Optional.of(USER_ID));

    filter.doFilterInternal(request, response, filterChain);

    verify(jwtSignatureVerifier).verifiedSubject(RAW_TOKEN);
    verify(filterChain).doFilter(request, response);
  }

  @Test
  void acceptsAHeaderThatCarriesTheTokenWithoutTheBearerPrefix() throws Exception {
    when(request.getHeader(AUTHORIZATION_HEADER)).thenReturn(RAW_TOKEN);
    when(jwtSignatureVerifier.verifiedSubject(RAW_TOKEN)).thenReturn(Optional.of(USER_ID));

    filter.doFilterInternal(request, response, filterChain);

    verify(filterChain).doFilter(request, response);
  }

  @Test
  void publishesTheVerifiedSubjectOnTheRequest() throws Exception {
    when(request.getHeader(AUTHORIZATION_HEADER)).thenReturn(BEARER_HEADER_VALUE);
    when(jwtSignatureVerifier.verifiedSubject(RAW_TOKEN)).thenReturn(Optional.of(USER_ID));

    filter.doFilterInternal(request, response, filterChain);

    verify(request).setAttribute(JwtSignatureFilter.VERIFIED_USER_ID, USER_ID);
  }

  @Test
  void writesUnauthorizedAndStopsTheChainWhenTheSignatureIsInvalid() throws Exception {
    when(request.getHeader(AUTHORIZATION_HEADER)).thenReturn(BEARER_HEADER_VALUE);
    when(jwtSignatureVerifier.verifiedSubject(RAW_TOKEN)).thenReturn(Optional.empty());
    when(response.getWriter()).thenReturn(responseWriter);

    filter.doFilterInternal(request, response, filterChain);

    verify(response).setStatus(EXPECTED_UNAUTHORIZED_STATUS_CODE);
    verify(response).setContentType(EXPECTED_CONTENT_TYPE);
    verify(filterChain, never()).doFilter(any(), any());
  }

  @Test
  void serializesTheErrorResponseWithTheExpectedReasonAndFlags() throws Exception {
    StringWriter responseBody = new StringWriter();
    when(request.getHeader(AUTHORIZATION_HEADER)).thenReturn(BEARER_HEADER_VALUE);
    when(jwtSignatureVerifier.verifiedSubject(RAW_TOKEN)).thenReturn(Optional.empty());
    when(response.getWriter()).thenReturn(new PrintWriter(responseBody));

    filter.doFilterInternal(request, response, filterChain);

    JsonNode body = objectMapper.readTree(responseBody.toString());
    assertThat(body.get("reason").asText(), is(EXPECTED_INVALID_TOKEN_REASON));
    assertThat(body.get("success").asBoolean(), is(EXPECTED_ERROR_SUCCESS_FLAG));
    assertThat(body.get("data").isNull(), is(true));
  }

  @Test
  void shouldNotFilterPublicPathsSoSwaggerStaysReachable() {
    when(request.getServletPath()).thenReturn("/swagger-ui/index.html");

    assertThat(filter.shouldNotFilter(request), is(true));
  }

  @Test
  void shouldFilterProxiedPaths() {
    when(request.getServletPath()).thenReturn("/backend/user");

    assertThat(filter.shouldNotFilter(request), is(false));
  }
}
