package com.lynq.iam.unit.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynq.iam.filter.AuthHeaderExistenceFilter;
import com.lynq.iam.filter.AuthHeaderValidationFilter;
import com.lynq.iam.filter.RequestUuidFilter;
import com.lynq.iam.service.JWTService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.web.servlet.FilterRegistrationBean;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

@ExtendWith(MockitoExtension.class)
class FilterConfigTest {

  private static final String URL_PATTERN_ALL = "/*";
  private static final String URL_PATTERN_VALIDATE = "/lynq-iam/auth/validate";
  private static final String URL_PATTERN_REFRESH = "/lynq-iam/auth/refresh";
  private static final String URL_PATTERN_UPDATE_PASSWORD = "/lynq-iam/auth/update-password";
  private static final String URL_PATTERN_USERINFO = "/lynq-iam/auth/userinfo";

  private static final int REQUEST_UUID_FILTER_ORDER = 0;
  private static final int AUTH_HEADER_EXISTENCE_FILTER_ORDER = 1;
  private static final int AUTH_HEADER_VALIDATION_FILTER_ORDER = 2;

  @Mock
  private ObjectMapper objectMapper;

  @Mock
  private JWTService jwtService;

  private FilterConfig filterConfig;

  @BeforeEach
  void setUp() {
    filterConfig = new FilterConfig();
  }

  @Test
  void createRequestUuidFilterReturnsRegistrationBeanWithRequestUuidFilter() {
    FilterRegistrationBean<RequestUuidFilter> registration = filterConfig.createRequestUuidFilter(objectMapper);

    assertThat(registration, is(notNullValue()));
    assertThat(registration.getFilter(), is(instanceOf(RequestUuidFilter.class)));
  }

  @Test
  void createRequestUuidFilterAppliesToAllUrlPatterns() {
    FilterRegistrationBean<RequestUuidFilter> registration = filterConfig.createRequestUuidFilter(objectMapper);

    assertThat(registration.getUrlPatterns(), contains(URL_PATTERN_ALL));
  }

  @Test
  void createRequestUuidFilterHasExpectedOrder() {
    FilterRegistrationBean<RequestUuidFilter> registration = filterConfig.createRequestUuidFilter(objectMapper);

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
  void createAuthHeaderExistenceFilterAppliesToProtectedAuthUrlPatterns() {
    FilterRegistrationBean<AuthHeaderExistenceFilter> registration =
        filterConfig.createAuthHeaderExistenceFilter(objectMapper);

    assertThat(registration.getUrlPatterns(), containsInAnyOrder(
        URL_PATTERN_VALIDATE,
        URL_PATTERN_REFRESH,
        URL_PATTERN_UPDATE_PASSWORD,
        URL_PATTERN_USERINFO
    ));
  }

  @Test
  void createAuthHeaderExistenceFilterHasExpectedOrder() {
    FilterRegistrationBean<AuthHeaderExistenceFilter> registration =
        filterConfig.createAuthHeaderExistenceFilter(objectMapper);

    assertThat(registration.getOrder(), is(AUTH_HEADER_EXISTENCE_FILTER_ORDER));
  }

  @Test
  void createAuthHeaderValidationFilterReturnsRegistrationBeanWithAuthHeaderValidationFilter() {
    FilterRegistrationBean<AuthHeaderValidationFilter> registration =
        filterConfig.createAuthHeaderValidationFilter(objectMapper, jwtService);

    assertThat(registration, is(notNullValue()));
    assertThat(registration.getFilter(), is(instanceOf(AuthHeaderValidationFilter.class)));
  }

  @Test
  void createAuthHeaderValidationFilterAppliesToValidationRequiredUrlPatterns() {
    FilterRegistrationBean<AuthHeaderValidationFilter> registration =
        filterConfig.createAuthHeaderValidationFilter(objectMapper, jwtService);

    assertThat(registration.getUrlPatterns(), containsInAnyOrder(
        URL_PATTERN_VALIDATE,
        URL_PATTERN_UPDATE_PASSWORD,
        URL_PATTERN_USERINFO
    ));
  }

  @Test
  void createAuthHeaderValidationFilterHasExpectedOrder() {
    FilterRegistrationBean<AuthHeaderValidationFilter> registration =
        filterConfig.createAuthHeaderValidationFilter(objectMapper, jwtService);

    assertThat(registration.getOrder(), is(AUTH_HEADER_VALIDATION_FILTER_ORDER));
  }
}
