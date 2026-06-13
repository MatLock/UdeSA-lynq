package com.lynq.iam.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynq.iam.controller.response.ErrorRestResponse;
import com.lynq.iam.service.JWTService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import java.io.PrintWriter;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthHeaderValidationFilterTest {

  private static final String AUTHORIZATION_HEADER = "Authorization";
  private static final String BEARER_PREFIX = "Bearer ";
  private static final String RAW_ACCESS_TOKEN = "eyJhbGciOiJIUzI1NiJ9.access.token";
  private static final String AUTH_HEADER_WITH_BEARER = BEARER_PREFIX + RAW_ACCESS_TOKEN;
  private static final String AUTH_HEADER_WITHOUT_BEARER = RAW_ACCESS_TOKEN;
  private static final String EXPECTED_INVALID_TOKEN_REASON = "Invalid or expired access token";
  private static final int EXPECTED_UNAUTHORIZED_STATUS_CODE = HttpStatus.UNAUTHORIZED.value();
  private static final String EXPECTED_CONTENT_TYPE = MediaType.APPLICATION_JSON_VALUE;
  private static final boolean TOKEN_VALID = true;
  private static final boolean TOKEN_INVALID = false;
  private static final boolean EXPECTED_ERROR_SUCCESS_FLAG = false;

  @Mock
  private ObjectMapper objectMapper;

  @Mock
  private JWTService jwtService;

  @Mock
  private HttpServletRequest request;

  @Mock
  private HttpServletResponse response;

  @Mock
  private FilterChain filterChain;

  @Mock
  private PrintWriter responseWriter;

  private AuthHeaderValidationFilter filter;

  @BeforeEach
  void setUp() {
    filter = new AuthHeaderValidationFilter(objectMapper, jwtService);
  }

  @Test
  void doFilterInternalStripsBearerPrefixBeforeValidatingToken() throws Exception {
    when(request.getHeader(AUTHORIZATION_HEADER)).thenReturn(AUTH_HEADER_WITH_BEARER);
    when(jwtService.isAccessTokenValid(RAW_ACCESS_TOKEN)).thenReturn(TOKEN_VALID);

    filter.doFilterInternal(request, response, filterChain);

    verify(jwtService).isAccessTokenValid(RAW_ACCESS_TOKEN);
  }

  @Test
  void doFilterInternalValidatesRawTokenWhenBearerPrefixAbsent() throws Exception {
    when(request.getHeader(AUTHORIZATION_HEADER)).thenReturn(AUTH_HEADER_WITHOUT_BEARER);
    when(jwtService.isAccessTokenValid(RAW_ACCESS_TOKEN)).thenReturn(TOKEN_VALID);

    filter.doFilterInternal(request, response, filterChain);

    verify(jwtService).isAccessTokenValid(RAW_ACCESS_TOKEN);
  }

  @Test
  void doFilterInternalDelegatesToFilterChainWhenAccessTokenIsValid() throws Exception {
    when(request.getHeader(AUTHORIZATION_HEADER)).thenReturn(AUTH_HEADER_WITH_BEARER);
    when(jwtService.isAccessTokenValid(RAW_ACCESS_TOKEN)).thenReturn(TOKEN_VALID);

    filter.doFilterInternal(request, response, filterChain);

    verify(filterChain).doFilter(request, response);
  }

  @Test
  void doFilterInternalWritesUnauthorizedErrorResponseWhenAccessTokenIsInvalid() throws Exception {
    when(request.getHeader(AUTHORIZATION_HEADER)).thenReturn(AUTH_HEADER_WITH_BEARER);
    when(jwtService.isAccessTokenValid(RAW_ACCESS_TOKEN)).thenReturn(TOKEN_INVALID);
    when(response.getWriter()).thenReturn(responseWriter);

    filter.doFilterInternal(request, response, filterChain);

    verify(response).setStatus(EXPECTED_UNAUTHORIZED_STATUS_CODE);
    verify(response).setContentType(EXPECTED_CONTENT_TYPE);
    verify(filterChain, never()).doFilter(any(), any());
  }

  @Test
  void doFilterInternalSerializesErrorResponseWithExpectedReasonAndFlagsWhenTokenInvalid() throws Exception {
    when(request.getHeader(AUTHORIZATION_HEADER)).thenReturn(AUTH_HEADER_WITH_BEARER);
    when(jwtService.isAccessTokenValid(RAW_ACCESS_TOKEN)).thenReturn(TOKEN_INVALID);
    when(response.getWriter()).thenReturn(responseWriter);
    ArgumentCaptor<ErrorRestResponse<Void>> errorCaptor = errorRestResponseCaptor();

    filter.doFilterInternal(request, response, filterChain);

    verify(objectMapper).writeValue(eq(responseWriter), errorCaptor.capture());
    ErrorRestResponse<Void> captured = errorCaptor.getValue();
    assertThat(captured.getReason(), is(EXPECTED_INVALID_TOKEN_REASON));
    assertThat(captured.isSuccess(), is(EXPECTED_ERROR_SUCCESS_FLAG));
    assertThat(captured.getData(), is(nullValue()));
  }

  @SuppressWarnings("unchecked")
  private static ArgumentCaptor<ErrorRestResponse<Void>> errorRestResponseCaptor() {
    return ArgumentCaptor.forClass(ErrorRestResponse.class);
  }
}