package com.lynq.bff.config;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

import com.lynq.bff.controller.impl.LlmControllerImpl;
import com.lynq.bff.ratelimit.RateLimitInterceptor;
import com.lynq.bff.ratelimit.RateLimitProperties;
import com.lynq.bff.ratelimit.RateLimitTier;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import java.util.Arrays;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.http.HttpHeaders;
import org.springframework.web.method.HandlerMethod;

class OpenApiConfigTest {

  private static final RateLimitProperties RATE_LIMITS = new RateLimitProperties(Map.of(
      RateLimitTier.LIGHT, new RateLimitProperties.Limit(30, 500),
      RateLimitTier.STANDARD, new RateLimitProperties.Limit(10, 100),
      RateLimitTier.HEAVY, new RateLimitProperties.Limit(5, 50)));

  private final OperationCustomizer customizer =
      new OpenApiConfig().rateLimitedOperationCustomizer(RATE_LIMITS);

  @Test
  void describesTheGateway() {
    OpenAPI openApi = new OpenApiConfig().lynqBffOpenAPI();

    assertThat(openApi.getInfo().getTitle(), is("lynq-bff"));
    assertThat(openApi.getInfo().getVersion(), is("v1"));
    assertThat(openApi.getInfo().getDescription(), containsString("/dmz"));
  }

  @Test
  void documentsTheRateLimitOnARateLimitedEndpoint() {
    Operation operation = customizer.customize(operationAnsweringOk(), handler("translateResume"));

    ApiResponse tooManyRequests = operation.getResponses().get(OpenApiConfig.TOO_MANY_REQUESTS);
    assertThat(tooManyRequests.getDescription(),
        containsString("HEAVY quota: 5 requests per minute and 50 per day"));
    assertThat(tooManyRequests.getHeaders(), hasKey(HttpHeaders.RETRY_AFTER));
    assertThat(operation.getResponses().get("200").getHeaders(),
        hasKey(RateLimitInterceptor.REMAINING_HEADER));
  }

  @Test
  void leavesAnEndpointWithoutARateLimitUntouched() {
    Operation operation =
        customizer.customize(operationAnsweringOk(), handler("refuseUnrelayedEndpoint"));

    assertThat(operation.getResponses(), not(hasKey(OpenApiConfig.TOO_MANY_REQUESTS)));
    assertThat(operation.getResponses().get("200").getHeaders(), is(nullValue()));
  }

  private static Operation operationAnsweringOk() {
    return new Operation().responses(new ApiResponses().addApiResponse("200", new ApiResponse()));
  }

  private static HandlerMethod handler(String methodName) {
    return new HandlerMethod(new LlmControllerImpl(null), Arrays.stream(
            LlmControllerImpl.class.getDeclaredMethods())
        .filter(method -> method.getName().equals(methodName))
        .findFirst()
        .orElseThrow());
  }
}
