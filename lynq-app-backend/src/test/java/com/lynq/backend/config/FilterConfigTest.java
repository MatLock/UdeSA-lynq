package com.lynq.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynq.backend.client.LynqIamClient;
import com.lynq.backend.filter.AuthHeaderExistenceFilter;
import com.lynq.backend.filter.IamAuthenticationFilter;
import com.lynq.backend.filter.RequestUuidFilter;
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

  private static final int REQUEST_UUID_FILTER_ORDER = 0;
  private static final int AUTH_HEADER_EXISTENCE_FILTER_ORDER = 1;
  private static final int IAM_AUTHENTICATION_FILTER_ORDER = 2;

  @Mock
  private LynqIamClient lynqIamClient;

  private ObjectMapper objectMapper;
  private FilterConfig filterConfig;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper();
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
        URL_PATTERN_ALL
    ));
  }

  @Test
  void createAuthHeaderExistenceFilterHasExpectedOrder() {
    FilterRegistrationBean<AuthHeaderExistenceFilter> registration =
        filterConfig.createAuthHeaderExistenceFilter(objectMapper);

    assertThat(registration.getOrder(), is(AUTH_HEADER_EXISTENCE_FILTER_ORDER));
  }

  @Test
  void createIamAuthenticationFilterReturnsRegistrationBeanWithIamAuthenticationFilter() {
    FilterRegistrationBean<IamAuthenticationFilter> registration =
        filterConfig.createIamAuthenticationFilter(lynqIamClient, objectMapper);

    assertThat(registration, is(notNullValue()));
    assertThat(registration.getFilter(), is(instanceOf(IamAuthenticationFilter.class)));
  }

  @Test
  void createIamAuthenticationFilterAppliesToAllUrlPatterns() {
    FilterRegistrationBean<IamAuthenticationFilter> registration =
        filterConfig.createIamAuthenticationFilter(lynqIamClient, objectMapper);

    assertThat(registration.getUrlPatterns(), contains(URL_PATTERN_ALL));
  }

  @Test
  void createIamAuthenticationFilterHasExpectedOrder() {
    FilterRegistrationBean<IamAuthenticationFilter> registration =
        filterConfig.createIamAuthenticationFilter(lynqIamClient, objectMapper);

    assertThat(registration.getOrder(), is(IAM_AUTHENTICATION_FILTER_ORDER));
  }
}
