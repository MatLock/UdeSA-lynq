package com.lynq.analytics.controller.impl;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lynq.analytics.config.SecurityConfig;
import com.lynq.analytics.controller.handler.ControllerExceptionHandler;
import com.lynq.analytics.exceptions.ForbiddenException;
import com.lynq.analytics.exceptions.NotFoundException;
import com.lynq.analytics.security.LynqUserPrincipal;
import com.lynq.analytics.security.Role;
import com.lynq.analytics.service.StandingService;
import com.lynq.analytics.stats.Standing;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@WebMvcTest(controllers = AnalyticsControllerImpl.class)
@Import({SecurityConfig.class, ControllerExceptionHandler.class})
class AnalyticsControllerImplTest {

  private static final String JOB_ID = "77777777-7777-7777-7777-777777777777";
  private static final String USER_ID = "11111111-1111-1111-1111-111111111111";
  private static final String STANDING_PATH = "/dmz/analytics/job/" + JOB_ID + "/standing";

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private StandingService standingService;

  @Test
  void answersTheStandingOfTheAuthenticatedCandidate() throws Exception {
    when(standingService.standing(JOB_ID, USER_ID))
        .thenReturn(new Standing(2, 6, 66.6, 72, 66.0));

    mockMvc.perform(as(Role.CANDIDATE))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success", is(true)))
        .andExpect(jsonPath("$.data.rank", is(2)))
        .andExpect(jsonPath("$.data.totalApplicants", is(6)))
        .andExpect(jsonPath("$.data.percentile", is(66.6)))
        .andExpect(jsonPath("$.data.score", is(72)))
        .andExpect(jsonPath("$.data.medianScore", is(66.0)));
  }

  @Test
  void sendsTheMedianAsNullWhenItIsWithheld() throws Exception {
    when(standingService.standing(JOB_ID, USER_ID))
        .thenReturn(new Standing(1, 2, 75.0, 70, null));

    mockMvc.perform(as(Role.CANDIDATE))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.medianScore", is(nullValue())));
  }

  @Test
  void refusesACompanyWithoutComputingAnything() throws Exception {
    mockMvc.perform(as(Role.COMPANY))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.success", is(false)))
        .andExpect(jsonPath("$.reason", is("Only users of type CANDIDATE can perform this action")));
    verifyNoInteractions(standingService);
  }

  @Test
  void passesTheRefusalOfACandidateWhoDidNotApply() throws Exception {
    when(standingService.standing(JOB_ID, USER_ID))
        .thenThrow(new ForbiddenException("Only a candidate who applied to the job post can "
            + "read their standing"));

    mockMvc.perform(as(Role.CANDIDATE))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.reason",
            is("Only a candidate who applied to the job post can read their standing")));
  }

  @Test
  void answersNotFoundForAnUnknownJobPost() throws Exception {
    when(standingService.standing(JOB_ID, USER_ID))
        .thenThrow(new NotFoundException("Job post '" + JOB_ID + "' not found"));

    mockMvc.perform(as(Role.CANDIDATE))
        .andExpect(status().isNotFound());
  }

  private static MockHttpServletRequestBuilder as(String role) {
    List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority(Role.PREFIX + role));
    LynqUserPrincipal principal = new LynqUserPrincipal(USER_ID, "janedoe", "jane@lynq.com",
        authorities);
    return get(STANDING_PATH)
        .with(authentication(new UsernamePasswordAuthenticationToken(principal, null, authorities)));
  }
}
