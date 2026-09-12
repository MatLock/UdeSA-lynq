package com.lynq.backend.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InternalTokenFilterTest {

  private static final String INTERNAL_TOKEN_HEADER = "lynq-internal-token";
  private static final String EXPECTED_TOKEN = "the-configured-internal-token";
  private static final String WRONG_TOKEN = "some-other-token";
  private static final String INTERNAL_PATH = "/internal/job-posts/ingest";
  private static final String DMZ_PATH = "/dmz/job";
  private static final int EXPECTED_UNAUTHORIZED_STATUS_CODE = HttpStatus.UNAUTHORIZED.value();
  private static final String EXPECTED_CONTENT_TYPE = MediaType.APPLICATION_JSON_VALUE;

  @Mock
  private HttpServletRequest request;

  @Mock
  private HttpServletResponse response;

  @Mock
  private FilterChain filterChain;

  @Mock
  private PrintWriter responseWriter;

  private InternalTokenFilter filter;

  @BeforeEach
  void setUp() {
    filter = new InternalTokenFilter(new ObjectMapper(), EXPECTED_TOKEN);
  }

  @Test
  void doFilterInternalDelegatesToFilterChainWhenTokenMatches() throws Exception {
    when(request.getHeader(INTERNAL_TOKEN_HEADER)).thenReturn(EXPECTED_TOKEN);

    filter.doFilterInternal(request, response, filterChain);

    verify(filterChain).doFilter(request, response);
  }

  @Test
  void doFilterInternalRejectsRequestWhenTokenIsMissing() throws Exception {
    when(request.getHeader(INTERNAL_TOKEN_HEADER)).thenReturn(null);
    when(response.getWriter()).thenReturn(responseWriter);

    filter.doFilterInternal(request, response, filterChain);

    verify(response).setStatus(EXPECTED_UNAUTHORIZED_STATUS_CODE);
    verify(response).setContentType(EXPECTED_CONTENT_TYPE);
    verify(filterChain, never()).doFilter(any(), any());
  }

  @Test
  void doFilterInternalRejectsRequestWhenTokenDoesNotMatch() throws Exception {
    when(request.getHeader(INTERNAL_TOKEN_HEADER)).thenReturn(WRONG_TOKEN);
    when(response.getWriter()).thenReturn(responseWriter);

    filter.doFilterInternal(request, response, filterChain);

    verify(response).setStatus(EXPECTED_UNAUTHORIZED_STATUS_CODE);
    verify(filterChain, never()).doFilter(any(), any());
  }

  @Test
  void doFilterInternalRejectsEveryRequestWhenNoTokenIsConfigured() throws Exception {
    InternalTokenFilter unconfigured = new InternalTokenFilter(new ObjectMapper(), "");
    when(request.getHeader(INTERNAL_TOKEN_HEADER)).thenReturn(EXPECTED_TOKEN);
    when(response.getWriter()).thenReturn(responseWriter);

    unconfigured.doFilterInternal(request, response, filterChain);

    verify(response).setStatus(EXPECTED_UNAUTHORIZED_STATUS_CODE);
    verify(filterChain, never()).doFilter(any(), any());
  }

  @Test
  void shouldNotFilterIsFalseForInternalPaths() {
    when(request.getServletPath()).thenReturn(INTERNAL_PATH);

    assertThat(filter.shouldNotFilter(request), is(false));
  }

  @Test
  void shouldNotFilterIsTrueForEveryOtherPath() {
    when(request.getServletPath()).thenReturn(DMZ_PATH);

    assertThat(filter.shouldNotFilter(request), is(true));
  }
}
