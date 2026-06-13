package com.lynq.iam.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

class OpenApiConfigTest {

  private static final String EXPECTED_BEARER_AUTH_KEY = "bearerAuth";
  private static final String EXPECTED_TITLE = "lynq-iam";
  private static final String EXPECTED_VERSION = "v1";
  private static final String EXPECTED_DESCRIPTION = "Identity and Access Management service for the Lynq platform";
  private static final String EXPECTED_BEARER_SCHEME = "bearer";
  private static final String EXPECTED_BEARER_FORMAT = "JWT or opaque refresh token";
  private static final String EXPECTED_SCHEME_DESCRIPTION =
      "Paste the token *without* the `Bearer ` prefix — Swagger UI adds it for you.";
  private static final SecurityScheme.Type EXPECTED_SCHEME_TYPE = SecurityScheme.Type.HTTP;

  private OpenApiConfig openApiConfig;

  @BeforeEach
  void setUp() {
    openApiConfig = new OpenApiConfig();
  }

  @Test
  void lynqIamOpenAPIReturnsNonNullOpenAPI() {
    OpenAPI openAPI = openApiConfig.lynqIamOpenAPI();

    assertThat(openAPI, is(notNullValue()));
  }

  @Test
  void lynqIamOpenAPIInfoHasExpectedTitle() {
    OpenAPI openAPI = openApiConfig.lynqIamOpenAPI();

    Info info = openAPI.getInfo();
    assertThat(info, is(notNullValue()));
    assertThat(info.getTitle(), is(EXPECTED_TITLE));
  }

  @Test
  void lynqIamOpenAPIInfoHasExpectedVersion() {
    OpenAPI openAPI = openApiConfig.lynqIamOpenAPI();

    assertThat(openAPI.getInfo().getVersion(), is(EXPECTED_VERSION));
  }

  @Test
  void lynqIamOpenAPIInfoHasExpectedDescription() {
    OpenAPI openAPI = openApiConfig.lynqIamOpenAPI();

    assertThat(openAPI.getInfo().getDescription(), is(EXPECTED_DESCRIPTION));
  }

  @Test
  void lynqIamOpenAPIRegistersBearerAuthSecurityScheme() {
    OpenAPI openAPI = openApiConfig.lynqIamOpenAPI();

    Components components = openAPI.getComponents();
    assertThat(components, is(notNullValue()));
    assertThat(components.getSecuritySchemes(), is(notNullValue()));
    assertThat(components.getSecuritySchemes(), hasKey(EXPECTED_BEARER_AUTH_KEY));
  }

  @Test
  void lynqIamOpenAPIBearerSchemeHasExpectedTypeAndScheme() {
    OpenAPI openAPI = openApiConfig.lynqIamOpenAPI();

    SecurityScheme scheme = openAPI.getComponents().getSecuritySchemes().get(EXPECTED_BEARER_AUTH_KEY);
    assertThat(scheme, is(notNullValue()));
    assertThat(scheme.getType(), is(EXPECTED_SCHEME_TYPE));
    assertThat(scheme.getScheme(), is(EXPECTED_BEARER_SCHEME));
  }

  @Test
  void lynqIamOpenAPIBearerSchemeHasExpectedBearerFormatAndDescription() {
    OpenAPI openAPI = openApiConfig.lynqIamOpenAPI();

    SecurityScheme scheme = openAPI.getComponents().getSecuritySchemes().get(EXPECTED_BEARER_AUTH_KEY);
    assertThat(scheme.getBearerFormat(), is(EXPECTED_BEARER_FORMAT));
    assertThat(scheme.getDescription(), is(EXPECTED_SCHEME_DESCRIPTION));
  }
}