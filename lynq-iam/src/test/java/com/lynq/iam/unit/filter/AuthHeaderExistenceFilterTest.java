package com.lynq.iam.unit.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynq.iam.controller.response.ErrorRestResponse;
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
class AuthHeaderExistenceFilterTest {

  private static final String AUTHORIZATION_HEADER = "Authorization";
  private static final String VALID_AUTH_HEADER_VALUE = "Bearer eyJhbGciOiJIUzI1NiJ9.access.token";
  private static final String BLANK_HEADER_VALUE = "   ";
  private static final String EXPECTED_MISSING_AUTH_REASON = "Missing Authorization header";
  private static final int EXPECTED_UNAUTHORIZED_STATUS_CODE = HttpStatus.UNAUTHORIZED.value();
  private static final String EXPECTED_CONTENT_TYPE = MediaType.APPLICATION_JSON_VALUE;
  private static final boolean EXPECTED_ERROR_SUCCESS_FLAG = false;
  private static final Object NO_DATA = null;

  @Mock
  private ObjectMapper objectMapper;

  @Mock
  private HttpServletRequest request;

  @Mock
  private HttpServletResponse response;

  @Mock
  private FilterChain filterChain;

  @Mock
  private PrintWriter responseWriter;

  private AuthHeaderExistenceFilter filter;

  @BeforeEach
  void setUp() {
    filter = new AuthHeaderExistenceFilter(objectMapper);
  }

  @Test
  void doFilterInternalDelegatesToFilterChainWhenAuthorizationHeaderIsPresent() throws Exception {
    when(request.getHeader(AUTHORIZATION_HEADER)).thenReturn(VALID_AUTH_HEADER_VALUE);

    filter.doFilterInternal(request, response, filterChain);

    verify(filterChain).doFilter(request, response);
  }

  @Test
  void doFilterInternalWritesUnauthorizedErrorResponseWhenAuthorizationHeaderIsNull() throws Exception {
    when(request.getHeader(AUTHORIZATION_HEADER)).thenReturn((String) NO_DATA);
    when(response.getWriter()).thenReturn(responseWriter);

    filter.doFilterInternal(request, response, filterChain);

    verify(response).setStatus(EXPECTED_UNAUTHORIZED_STATUS_CODE);
    verify(response).setContentType(EXPECTED_CONTENT_TYPE);
    verify(filterChain, never()).doFilter(any(), any());
  }

  @Test
  void doFilterInternalWritesUnauthorizedErrorResponseWhenAuthorizationHeaderIsBlank() throws Exception {
    when(request.getHeader(AUTHORIZATION_HEADER)).thenReturn(BLANK_HEADER_VALUE);
    when(response.getWriter()).thenReturn(responseWriter);

    filter.doFilterInternal(request, response, filterChain);

    verify(response).setStatus(EXPECTED_UNAUTHORIZED_STATUS_CODE);
    verify(response).setContentType(EXPECTED_CONTENT_TYPE);
    verify(filterChain, never()).doFilter(any(), any());
  }

  @Test
  void doFilterInternalSerializesErrorResponseWithExpectedReasonAndFlagsWhenHeaderMissing() throws Exception {
    when(request.getHeader(AUTHORIZATION_HEADER)).thenReturn((String) NO_DATA);
    when(response.getWriter()).thenReturn(responseWriter);
    ArgumentCaptor<ErrorRestResponse<Void>> errorCaptor = errorRestResponseCaptor();

    filter.doFilterInternal(request, response, filterChain);

    verify(objectMapper).writeValue(eq(responseWriter), errorCaptor.capture());
    ErrorRestResponse<Void> captured = errorCaptor.getValue();
    assertThat(captured.getReason(), is(EXPECTED_MISSING_AUTH_REASON));
    assertThat(captured.isSuccess(), is(EXPECTED_ERROR_SUCCESS_FLAG));
    assertThat(captured.getData(), is(nullValue()));
  }

  @SuppressWarnings("unchecked")
  private static ArgumentCaptor<ErrorRestResponse<Void>> errorRestResponseCaptor() {
    return ArgumentCaptor.forClass(ErrorRestResponse.class);
  }
}