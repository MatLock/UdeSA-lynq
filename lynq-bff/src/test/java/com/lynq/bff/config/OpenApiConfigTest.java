package com.lynq.bff.config;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.Test;

class OpenApiConfigTest {

  @Test
  void describesTheGateway() {
    OpenAPI openApi = new OpenApiConfig().lynqBffOpenAPI();

    assertThat(openApi.getInfo().getTitle(), is("lynq-bff"));
    assertThat(openApi.getInfo().getVersion(), is("v1"));
    assertThat(openApi.getInfo().getDescription(), containsString("/dmz"));
  }
}
