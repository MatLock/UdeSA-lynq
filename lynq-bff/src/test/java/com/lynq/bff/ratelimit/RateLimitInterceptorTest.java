package com.lynq.bff.ratelimit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lynq.bff.config.RateLimitConfig;
import com.lynq.bff.config.SecurityConfig;
import com.lynq.bff.controller.handler.ControllerExceptionHandler;
import com.lynq.bff.controller.impl.LlmControllerImpl;
import com.lynq.bff.security.LynqUserPrincipal;
import com.lynq.bff.service.LlmService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@WebMvcTest(controllers = LlmControllerImpl.class)
@Import({SecurityConfig.class, ControllerExceptionHandler.class, RateLimitConfig.class,
    RateLimitInterceptorTest.Metrics.class})
@TestPropertySource(properties = {
    "lynq.rate-limit.tiers.light.per-minute=30",
    "lynq.rate-limit.tiers.light.per-day=500",
    "lynq.rate-limit.tiers.standard.per-minute=2",
    "lynq.rate-limit.tiers.standard.per-day=100",
    "lynq.rate-limit.tiers.heavy.per-minute=1",
    "lynq.rate-limit.tiers.heavy.per-day=50"
})
class RateLimitInterceptorTest {

  private static final String REQUEST_UUID_HEADER = "lynq-request-uuid";
  private static final String REQUEST_UUID = "018f9c3a-2b1d-7c4e-9a6f-1e2d3c4b5a99";
  private static final String AUTHORIZATION = "Bearer access-token";

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private MeterRegistry meterRegistry;

  @MockitoBean
  private LlmService llmService;

  @Test
  void answersTooManyRequestsOnceTheCallerUsesUpTheirQuota() throws Exception {
    String userId = "aaaaaaaa-0000-0000-0000-000000000001";
    mockMvc.perform(skillEnhanceAs(userId)).andExpect(status().isOk());
    mockMvc.perform(skillEnhanceAs(userId)).andExpect(status().isOk());

    mockMvc.perform(skillEnhanceAs(userId))
        .andExpect(status().isTooManyRequests())
        .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
        .andExpect(jsonPath("$.success", is(false)))
        .andExpect(jsonPath("$.code", is("RATE_LIMIT_EXCEEDED")));

    verify(llmService, times(2)).enhanceSkills(any(), any());
  }

  @Test
  void tellsTheCallerHowManyRequestsTheyHaveLeft() throws Exception {
    String userId = "aaaaaaaa-0000-0000-0000-000000000002";

    mockMvc.perform(skillEnhanceAs(userId))
        .andExpect(status().isOk())
        .andExpect(header().string(RateLimitInterceptor.REMAINING_HEADER, "1"));
  }

  @Test
  void eachUserHasTheirOwnQuota() throws Exception {
    mockMvc.perform(translateAs("aaaaaaaa-0000-0000-0000-000000000003"))
        .andExpect(status().isOk());
    mockMvc.perform(translateAs("aaaaaaaa-0000-0000-0000-000000000003"))
        .andExpect(status().isTooManyRequests());

    mockMvc.perform(translateAs("aaaaaaaa-0000-0000-0000-000000000004"))
        .andExpect(status().isOk());
  }

  @Test
  void endpointsWithoutARateLimitAreNeverThrottled() throws Exception {
    String userId = "aaaaaaaa-0000-0000-0000-000000000005";

    for (int attempt = 0; attempt < 5; attempt++) {
      mockMvc.perform(as(userId, post("/parse-resume")))
          .andExpect(status().isForbidden())
          .andExpect(header().doesNotExist(RateLimitInterceptor.REMAINING_HEADER));
    }
  }

  @Test
  void countsEveryRejectionByTier() throws Exception {
    String userId = "aaaaaaaa-0000-0000-0000-000000000006";
    double before = rejected("HEAVY");

    mockMvc.perform(translateAs(userId)).andExpect(status().isOk());
    mockMvc.perform(translateAs(userId)).andExpect(status().isTooManyRequests());

    assertThat(rejected("HEAVY") - before, is(1.0));
  }

  private double rejected(String tier) {
    return meterRegistry.counter(RateLimitInterceptor.REJECTED_METRIC, "tier", tier).count();
  }

  private MockHttpServletRequestBuilder skillEnhanceAs(String userId) {
    return as(userId, post("/skill-enhance")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"title\":\"Backend\"}"));
  }

  private MockHttpServletRequestBuilder translateAs(String userId) {
    return as(userId, post("/translate")
        .contentType(MediaType.APPLICATION_JSON)
        .content("{\"resume\":{},\"language\":\"FR\"}"));
  }

  private static MockHttpServletRequestBuilder as(String userId,
                                                  MockHttpServletRequestBuilder builder) {
    List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("R_CANDIDATE"));
    LynqUserPrincipal principal =
        new LynqUserPrincipal(userId, "janedoe", "jane@lynq.com", authorities, AUTHORIZATION);
    return builder
        .header(REQUEST_UUID_HEADER, REQUEST_UUID)
        .header(HttpHeaders.AUTHORIZATION, AUTHORIZATION)
        .with(authentication(new UsernamePasswordAuthenticationToken(principal, null, authorities)));
  }

  @TestConfiguration
  static class Metrics {

    @Bean
    MeterRegistry meterRegistry() {
      return new SimpleMeterRegistry();
    }
  }
}
