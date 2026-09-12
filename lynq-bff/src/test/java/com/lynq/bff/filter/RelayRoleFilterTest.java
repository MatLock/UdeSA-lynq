package com.lynq.bff.filter;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynq.bff.security.Role;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class RelayRoleFilterTest {

  private static final String USER_ID = "11111111-1111-1111-1111-111111111111";
  private static final String CANDIDATE_ONLY_PATH = "/user/resume";
  private static final String COMPANY_ONLY_PATH = "/job/mine";
  private static final String UNRULED_PATH = "/job/018f9c3a/details";
  private static final int EXPECTED_FORBIDDEN_STATUS_CODE = HttpStatus.FORBIDDEN.value();
  private static final String EXPECTED_CANDIDATE_REASON =
      "Only users of type CANDIDATE can perform this action";
  private static final String EXPECTED_COMPANY_REASON =
      "Only users of type COMPANY can perform this action";
  private static final boolean EXPECTED_ERROR_SUCCESS_FLAG = false;

  @Mock
  private HttpServletRequest request;

  @Mock
  private HttpServletResponse response;

  @Mock
  private FilterChain filterChain;

  private RelayRoleFilter filter;
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper();
    filter = new RelayRoleFilter(objectMapper);
  }

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void bouncesACandidateOnlyRouteWhenTheCallerIsACompany() throws Exception {
    authenticateWith(Role.COMPANY);
    when(request.getMethod()).thenReturn("GET");
    when(request.getServletPath()).thenReturn(CANDIDATE_ONLY_PATH);
    when(response.getWriter()).thenReturn(new PrintWriter(new StringWriter()));

    filter.doFilterInternal(request, response, filterChain);

    verify(response).setStatus(EXPECTED_FORBIDDEN_STATUS_CODE);
    verify(response).setContentType(MediaType.APPLICATION_JSON_VALUE);
    verify(filterChain, never()).doFilter(any(), any());
  }

  @Test
  void bouncesACompanyOnlyRouteWhenTheCallerIsACandidate() throws Exception {
    StringWriter responseBody = new StringWriter();
    authenticateWith(Role.CANDIDATE);
    when(request.getMethod()).thenReturn("GET");
    when(request.getServletPath()).thenReturn(COMPANY_ONLY_PATH);
    when(response.getWriter()).thenReturn(new PrintWriter(responseBody));

    filter.doFilterInternal(request, response, filterChain);

    JsonNode body = objectMapper.readTree(responseBody.toString());
    assertThat(body.get("reason").asText(), is(EXPECTED_COMPANY_REASON));
    assertThat(body.get("success").asBoolean(), is(EXPECTED_ERROR_SUCCESS_FLAG));
    assertThat(body.get("data").isNull(), is(true));
    verify(filterChain, never()).doFilter(any(), any());
  }

  @Test
  void answersTheSameEnvelopeTheDownstreamServicesUse() throws Exception {
    StringWriter responseBody = new StringWriter();
    authenticateWith(Role.COMPANY);
    when(request.getMethod()).thenReturn("GET");
    when(request.getServletPath()).thenReturn(CANDIDATE_ONLY_PATH);
    when(response.getWriter()).thenReturn(new PrintWriter(responseBody));

    filter.doFilterInternal(request, response, filterChain);

    assertThat(objectMapper.readTree(responseBody.toString()).get("reason").asText(),
        is(EXPECTED_CANDIDATE_REASON));
  }

  @Test
  void relaysACandidateOnlyRouteWhenTheCallerIsACandidate() throws Exception {
    authenticateWith(Role.CANDIDATE);
    when(request.getMethod()).thenReturn("GET");
    when(request.getServletPath()).thenReturn(CANDIDATE_ONLY_PATH);

    filter.doFilterInternal(request, response, filterChain);

    verify(filterChain).doFilter(request, response);
    verify(response, never()).setStatus(EXPECTED_FORBIDDEN_STATUS_CODE);
  }

  @Test
  void relaysARouteNoRuleCoversWhateverTheCallerHolds() throws Exception {
    authenticateWith(Role.COMPANY);
    when(request.getMethod()).thenReturn("GET");
    when(request.getServletPath()).thenReturn(UNRULED_PATH);

    filter.doFilterInternal(request, response, filterChain);

    verify(filterChain).doFilter(request, response);
  }

  @Test
  void bouncesARuledRouteWhenTheTokenCarriedNoRoles() throws Exception {
    authenticateWithoutRoles();
    when(request.getMethod()).thenReturn("GET");
    when(request.getServletPath()).thenReturn(CANDIDATE_ONLY_PATH);
    when(response.getWriter()).thenReturn(new PrintWriter(new StringWriter()));

    filter.doFilterInternal(request, response, filterChain);

    verify(response).setStatus(EXPECTED_FORBIDDEN_STATUS_CODE);
    verify(filterChain, never()).doFilter(any(), any());
  }

  @Test
  void bouncesARuledRouteWhenThereIsNoAuthenticationAtAll() throws Exception {
    when(request.getMethod()).thenReturn("GET");
    when(request.getServletPath()).thenReturn(CANDIDATE_ONLY_PATH);
    when(response.getWriter()).thenReturn(new PrintWriter(new StringWriter()));

    filter.doFilterInternal(request, response, filterChain);

    verify(response).setStatus(EXPECTED_FORBIDDEN_STATUS_CODE);
    verify(filterChain, never()).doFilter(any(), any());
  }

  @Test
  void shouldNotFilterPublicPathsSoSwaggerStaysReachable() {
    when(request.getServletPath()).thenReturn("/swagger-ui/index.html");

    assertThat(filter.shouldNotFilter(request), is(true));
  }

  @Test
  void shouldFilterRelayedPaths() {
    when(request.getServletPath()).thenReturn("/user/resume");

    assertThat(filter.shouldNotFilter(request), is(false));
  }

  @Test
  void shouldNotFilterTheAuthRoutesWhereThereIsNoVerifiedCallerToBounce() {
    when(request.getServletPath()).thenReturn("/auth/login/username");

    assertThat(filter.shouldNotFilter(request), is(true));
  }

  @Test
  void shouldNotFilterTheRefreshWhoseCredentialCarriesNoRoles() {
    when(request.getServletPath()).thenReturn("/auth/refresh");

    assertThat(filter.shouldNotFilter(request), is(true));
  }

  private static void authenticateWith(String role) {
    List<GrantedAuthority> authorities =
        List.of(new SimpleGrantedAuthority(Role.PREFIX + role));
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken(USER_ID, null, authorities));
  }

  private static void authenticateWithoutRoles() {
    SecurityContextHolder.getContext().setAuthentication(
        new UsernamePasswordAuthenticationToken(USER_ID, null, List.of()));
  }
}
