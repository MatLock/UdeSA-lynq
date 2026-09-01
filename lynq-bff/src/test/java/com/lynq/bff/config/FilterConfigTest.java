package com.lynq.bff.config;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.lessThan;
import static org.hamcrest.Matchers.notNullValue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynq.bff.filter.AuthHeaderExistenceFilter;
import com.lynq.bff.filter.JwtSignatureFilter;
import com.lynq.bff.filter.RequestUuidFilter;
import com.lynq.bff.security.JwtSignatureVerifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.web.filter.CorsFilter;

@ExtendWith(MockitoExtension.class)
class FilterConfigTest {

  private static final String URL_PATTERN_ALL = "/*";

  private static final int REQUEST_UUID_FILTER_ORDER = 0;
  private static final int AUTH_HEADER_EXISTENCE_FILTER_ORDER = 1;
  private static final int JWT_SIGNATURE_FILTER_ORDER = 2;

  @Mock
  private JwtSignatureVerifier jwtSignatureVerifier;

  private ObjectMapper objectMapper;
  private FilterConfig filterConfig;
  private CorsConfig corsConfig;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper();
    filterConfig = new FilterConfig();
    corsConfig = new CorsConfig();
  }

  @Test
  void createRequestUuidFilterReturnsRegistrationBeanWithRequestUuidFilter() {
    FilterRegistrationBean<RequestUuidFilter> registration =
        filterConfig.createRequestUuidFilter(objectMapper);

    assertThat(registration, is(notNullValue()));
    assertThat(registration.getFilter(), is(instanceOf(RequestUuidFilter.class)));
  }

  @Test
  void createRequestUuidFilterAppliesToAllUrlPatterns() {
    FilterRegistrationBean<RequestUuidFilter> registration =
        filterConfig.createRequestUuidFilter(objectMapper);

    assertThat(registration.getUrlPatterns(), contains(URL_PATTERN_ALL));
  }

  @Test
  void createRequestUuidFilterHasExpectedOrder() {
    FilterRegistrationBean<RequestUuidFilter> registration =
        filterConfig.createRequestUuidFilter(objectMapper);

    assertThat(registration.getOrder(), is(REQUEST_UUID_FILTER_ORDER));
  }

  @Test
  void createAuthHeaderExistenceFilterReturnsRegistrationBeanWithAuthHeaderExistenceFilter() {
    FilterRegistrationBean<AuthHeaderExistenceFilter> registration =
        filterConfig.createAuthHeaderExistenceFilter(objectMapper);

    assertThat(registration, is(notNullValue()));
    assertThat(registration.getFilter(), is(instanceOf(AuthHeaderExistenceFilter.class)));
  }

  @Test
  void createAuthHeaderExistenceFilterHasExpectedOrder() {
    FilterRegistrationBean<AuthHeaderExistenceFilter> registration =
        filterConfig.createAuthHeaderExistenceFilter(objectMapper);

    assertThat(registration.getOrder(), is(AUTH_HEADER_EXISTENCE_FILTER_ORDER));
  }

  @Test
  void createJwtSignatureFilterReturnsRegistrationBeanWithJwtSignatureFilter() {
    FilterRegistrationBean<JwtSignatureFilter> registration =
        filterConfig.createJwtSignatureFilter(jwtSignatureVerifier, objectMapper);

    assertThat(registration, is(notNullValue()));
    assertThat(registration.getFilter(), is(instanceOf(JwtSignatureFilter.class)));
  }

  @Test
  void createJwtSignatureFilterHasExpectedOrder() {
    FilterRegistrationBean<JwtSignatureFilter> registration =
        filterConfig.createJwtSignatureFilter(jwtSignatureVerifier, objectMapper);

    assertThat(registration.getOrder(), is(JWT_SIGNATURE_FILTER_ORDER));
  }

  @Test
  void corsFilterRunsBeforeEveryAuthenticationFilter() {
    FilterRegistrationBean<CorsFilter> cors =
        corsConfig.createCorsFilter(corsConfig.corsConfigurationSource());

    assertThat(cors.getOrder(), is(lessThan(REQUEST_UUID_FILTER_ORDER)));
    assertThat(cors.getUrlPatterns(), contains(URL_PATTERN_ALL));
  }
}
