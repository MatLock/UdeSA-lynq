package com.lynq.iam.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynq.iam.controller.response.ErrorRestResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
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
class RequestUuidFilterTest {

  private static final String REQUEST_UUID_HEADER = "lynq-request-uuid";
  private static final String SAMPLE_REQUEST_UUID = "550e8400-e29b-41d4-a716-446655440000";
  private static final String BLANK_HEADER_VALUE = "   ";
  private static final String EXPECTED_MISSING_HEADER_REASON = "Missing required header";
  private static final int EXPECTED_FORBIDDEN_STATUS_CODE = HttpStatus.FORBIDDEN.value();
  private static final String EXPECTED_CONTENT_TYPE = MediaType.APPLICATION_JSON_VALUE;
  private static final boolean EXPECTED_ERROR_SUCCESS_FLAG = false;
  private static final Object NO_DATA = null;

  private static final String NON_WHITELISTED_PATH = "/lynq-iam/auth/userinfo";
  private static final String SWAGGER_UI_HTML_PATH = "/swagger-ui.html";
  private static final String SWAGGER_UI_INDEX_PATH = "/swagger-ui/index.html";
  private static final String V3_API_DOCS_PATH = "/v3/api-docs";
  private static final String SWAGGER_RESOURCES_PATH = "/swagger-resources/configuration/ui";
  private static final String WEBJARS_PATH = "/webjars/some-asset.js";

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

  private RequestUuidFilter filter;

  @BeforeEach
  void setUp() {
    filter = new RequestUuidFilter(objectMapper);
  }

  @AfterEach
  void tearDown() {
    MDC.clear();
  }

  @Test
  void doFilterInternalEchoesRequestUuidAsResponseHeaderWhenPresent() throws Exception {
    when(request.getHeader(REQUEST_UUID_HEADER)).thenReturn(SAMPLE_REQUEST_UUID);

    filter.doFilterInternal(request, response, filterChain);

    verify(response).setHeader(REQUEST_UUID_HEADER, SAMPLE_REQUEST_UUID);
  }

  @Test
  void doFilterInternalDelegatesToFilterChainWhenRequestUuidHeaderIsPresent() throws Exception {
    when(request.getHeader(REQUEST_UUID_HEADER)).thenReturn(SAMPLE_REQUEST_UUID);

    filter.doFilterInternal(request, response, filterChain);

    verify(filterChain).doFilter(request, response);
  }

  @Test
  void doFilterInternalWritesForbiddenErrorResponseWhenRequestUuidHeaderIsNull() throws Exception {
    when(request.getHeader(REQUEST_UUID_HEADER)).thenReturn((String) NO_DATA);
    when(response.getWriter()).thenReturn(responseWriter);

    filter.doFilterInternal(request, response, filterChain);

    verify(response).setStatus(EXPECTED_FORBIDDEN_STATUS_CODE);
    verify(response).setContentType(EXPECTED_CONTENT_TYPE);
    verify(filterChain, never()).doFilter(any(), any());
  }

  @Test
  void doFilterInternalWritesForbiddenErrorResponseWhenRequestUuidHeaderIsBlank() throws Exception {
    when(request.getHeader(REQUEST_UUID_HEADER)).thenReturn(BLANK_HEADER_VALUE);
    when(response.getWriter()).thenReturn(responseWriter);

    filter.doFilterInternal(request, response, filterChain);

    verify(response).setStatus(EXPECTED_FORBIDDEN_STATUS_CODE);
    verify(response).setContentType(EXPECTED_CONTENT_TYPE);
    verify(filterChain, never()).doFilter(any(), any());
  }

  @Test
  void doFilterInternalSerializesErrorResponseWithExpectedReasonAndFlagsWhenHeaderMissing() throws Exception {
    when(request.getHeader(REQUEST_UUID_HEADER)).thenReturn((String) NO_DATA);
    when(response.getWriter()).thenReturn(responseWriter);
    ArgumentCaptor<ErrorRestResponse<Void>> errorCaptor = errorRestResponseCaptor();

    filter.doFilterInternal(request, response, filterChain);

    verify(objectMapper).writeValue(eq(responseWriter), errorCaptor.capture());
    ErrorRestResponse<Void> captured = errorCaptor.getValue();
    assertThat(captured.getReason(), is(EXPECTED_MISSING_HEADER_REASON));
    assertThat(captured.isSuccess(), is(EXPECTED_ERROR_SUCCESS_FLAG));
    assertThat(captured.getData(), is(nullValue()));
  }

  @Test
  void shouldNotFilterReturnsFalseForNonWhitelistedPath() {
    when(request.getServletPath()).thenReturn(NON_WHITELISTED_PATH);

    assertThat(filter.shouldNotFilter(request), is(false));
  }

  @Test
  void shouldNotFilterReturnsTrueForSwaggerUiHtmlPath() {
    when(request.getServletPath()).thenReturn(SWAGGER_UI_HTML_PATH);

    assertThat(filter.shouldNotFilter(request), is(true));
  }

  @Test
  void shouldNotFilterReturnsTrueForSwaggerUiPrefixPath() {
    when(request.getServletPath()).thenReturn(SWAGGER_UI_INDEX_PATH);

    assertThat(filter.shouldNotFilter(request), is(true));
  }

  @Test
  void shouldNotFilterReturnsTrueForV3ApiDocsPrefixPath() {
    when(request.getServletPath()).thenReturn(V3_API_DOCS_PATH);

    assertThat(filter.shouldNotFilter(request), is(true));
  }

  @Test
  void shouldNotFilterReturnsTrueForSwaggerResourcesPrefixPath() {
    when(request.getServletPath()).thenReturn(SWAGGER_RESOURCES_PATH);

    assertThat(filter.shouldNotFilter(request), is(true));
  }

  @Test
  void shouldNotFilterReturnsTrueForWebjarsPrefixPath() {
    when(request.getServletPath()).thenReturn(WEBJARS_PATH);

    assertThat(filter.shouldNotFilter(request), is(true));
  }

  @SuppressWarnings("unchecked")
  private static ArgumentCaptor<ErrorRestResponse<Void>> errorRestResponseCaptor() {
    return ArgumentCaptor.forClass(ErrorRestResponse.class);
  }
}