package com.lynq.analytics.security;

import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lynq.analytics.config.SecurityConfig;
import com.lynq.analytics.controller.handler.ControllerExceptionHandler;
import com.lynq.analytics.controller.response.GlobalRestResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(controllers = HasRoleAuthorizationTest.RoleScopedController.class)
@Import({SecurityConfig.class, ControllerExceptionHandler.class,
    HasRoleAuthorizationTest.RoleScopedController.class})
class HasRoleAuthorizationTest {

  private static final String USER_ID = "11111111-1111-1111-1111-111111111111";
  private static final String USERNAME = "janedoe";
  private static final String EMAIL = "jane@lynq.com";

  private static final String COMPANY_ROUTE = "/role-scoped/company";
  private static final String CANDIDATE_ROUTE = "/role-scoped/candidate";
  private static final String ANY_AUTHENTICATED_ROUTE = "/role-scoped/any";

  private static final String ONLY_COMPANIES = "Only users of type COMPANY can perform this action";
  private static final String ONLY_CANDIDATES =
      "Only users of type CANDIDATE can perform this action";

  @Autowired
  private MockMvc mockMvc;

  @Test
  void aCompanyReachesACompanyRoute() throws Exception {
    mockMvc.perform(as(Role.COMPANY, COMPANY_ROUTE))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success", is(true)))
        .andExpect(jsonPath("$.data", is(USER_ID)));
  }

  @Test
  void aCandidateIsRefusedACompanyRouteNamingTheRoleItLacks() throws Exception {
    mockMvc.perform(as(Role.CANDIDATE, COMPANY_ROUTE))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.success", is(false)))
        .andExpect(jsonPath("$.reason", is(ONLY_COMPANIES)));
  }

  @Test
  void aCandidateReachesACandidateRoute() throws Exception {
    mockMvc.perform(as(Role.CANDIDATE, CANDIDATE_ROUTE))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data", is(USER_ID)));
  }

  @Test
  void aCompanyIsRefusedACandidateRouteNamingTheRoleItLacks() throws Exception {
    mockMvc.perform(as(Role.COMPANY, CANDIDATE_ROUTE))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.success", is(false)))
        .andExpect(jsonPath("$.reason", is(ONLY_CANDIDATES)));
  }

  @Test
  void aCallerWithoutAnyRoleIsRefusedBothRoleScopedRoutes() throws Exception {
    mockMvc.perform(as(null, COMPANY_ROUTE))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.reason", is(ONLY_COMPANIES)));
    mockMvc.perform(as(null, CANDIDATE_ROUTE))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.reason", is(ONLY_CANDIDATES)));
  }

  @Test
  void aRouteWithoutHasRoleIsReachableWhateverRoleTheCallerHolds() throws Exception {
    mockMvc.perform(as(Role.COMPANY, ANY_AUTHENTICATED_ROUTE))
        .andExpect(status().isOk());
    mockMvc.perform(as(Role.CANDIDATE, ANY_AUTHENTICATED_ROUTE))
        .andExpect(status().isOk());
    mockMvc.perform(as(null, ANY_AUTHENTICATED_ROUTE))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data", is(USER_ID)));
  }

  private static MockHttpServletRequestBuilder as(String role, String path) {
    return get(path).with(authentication(authenticationWith(role)));
  }

  private static UsernamePasswordAuthenticationToken authenticationWith(String role) {
    List<GrantedAuthority> authorities = role == null
        ? List.of()
        : List.of(new SimpleGrantedAuthority(Role.PREFIX + role));
    LynqUserPrincipal principal = new LynqUserPrincipal(USER_ID, USERNAME, EMAIL, authorities);
    return new UsernamePasswordAuthenticationToken(principal, null, authorities);
  }

  @RestController
  @RequestMapping("/role-scoped")
  static class RoleScopedController {

    @HasRole(Role.COMPANY)
    @GetMapping("/company")
    public ResponseEntity<GlobalRestResponse<String>> company(
        @AuthenticationPrincipal LynqUserPrincipal principal) {
      return ResponseEntity.ok(new GlobalRestResponse<>(true, principal.getId()));
    }

    @HasRole(Role.CANDIDATE)
    @GetMapping("/candidate")
    public ResponseEntity<GlobalRestResponse<String>> candidate(
        @AuthenticationPrincipal LynqUserPrincipal principal) {
      return ResponseEntity.ok(new GlobalRestResponse<>(true, principal.getId()));
    }

    @GetMapping("/any")
    public ResponseEntity<GlobalRestResponse<String>> anyAuthenticated(
        @AuthenticationPrincipal LynqUserPrincipal principal) {
      return ResponseEntity.ok(new GlobalRestResponse<>(true, principal.getId()));
    }
  }
}
