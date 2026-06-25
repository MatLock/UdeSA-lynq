package com.lynq.backend.filter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
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
  private HttpServletRequest request;

  @Mock
  private HttpServletResponse response;

  @Mock
  private FilterChain filterChain;

  @Mock
  private PrintWriter responseWriter;

  private AuthHeaderExistenceFilter filter;
  private ObjectMapper objectMapper;


  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper();
    objectMapper.registerModule(new JavaTimeModule());
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
    StringWriter responseBody = new StringWriter();
    when(request.getHeader(AUTHORIZATION_HEADER)).thenReturn((String) NO_DATA);
    when(response.getWriter()).thenReturn(new PrintWriter(responseBody));

    filter.doFilterInternal(request, response, filterChain);

    JsonNode body = objectMapper.readTree(responseBody.toString());
    assertThat(body.get("reason").asText(), is(EXPECTED_MISSING_AUTH_REASON));
    assertThat(body.get("success").asBoolean(), is(EXPECTED_ERROR_SUCCESS_FLAG));
    assertThat(body.get("data").isNull(), is(true));
  }

  @Test
  void shouldNotFilterPublicPathsSoTheyAreNotBlockedWhenAuthorizationHeaderIsMissing() {
    when(request.getServletPath()).thenReturn("/swagger-ui/index.html");

    assertThat(filter.shouldNotFilter(request), is(true));
  }

  @Test
  void shouldFilterNonPublicPaths() {
    when(request.getServletPath()).thenReturn("/api/v1/resource");

    assertThat(filter.shouldNotFilter(request), is(false));
  }
}