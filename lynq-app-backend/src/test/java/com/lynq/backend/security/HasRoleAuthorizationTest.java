package com.lynq.backend.security;

import com.lynq.backend.config.SecurityConfig;
import com.lynq.backend.controller.handler.ControllerExceptionHandler;
import com.lynq.backend.controller.impl.CompanyControllerImpl;
import com.lynq.backend.controller.impl.JobControllerImpl;
import com.lynq.backend.controller.impl.UserControllerImpl;
import com.lynq.backend.controller.response.PagedRestResponse;
import com.lynq.backend.service.CompanyService;
import com.lynq.backend.service.JobService;
import com.lynq.backend.service.UserService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {UserControllerImpl.class, JobControllerImpl.class,
    CompanyControllerImpl.class})
@Import({SecurityConfig.class, ControllerExceptionHandler.class})
@TestPropertySource(properties = "server.servlet.context-path=")
class HasRoleAuthorizationTest {

  private static final String USER_ID = "550e8400-e29b-41d4-a716-446655440000";
  private static final String USERNAME = "johndoe";
  private static final String EMAIL = "johndoe@example.com";
  private static final String REQUEST_UUID_HEADER = "lynq-request-uuid";
  private static final String REQUEST_UUID = "6ba7b810-9dad-11d1-80b4-00c04fd430c8";
  private static final String JOB_ID = "018f9c3a-2b1d-7c4e-9a6f-1e2d3c4b5a60";
  private static final String RESUME_ID = "018f9c3a-2b1d-7c4e-9a6f-1e2d3c4b5a61";

  private static final String ONLY_CANDIDATES =
      "Only users of type CANDIDATE can perform this action";
  private static final String ONLY_COMPANIES =
      "Only users of type COMPANY can perform this action";

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private UserService userService;

  @MockitoBean
  private JobService jobService;

  @MockitoBean
  private CompanyService companyService;

  static List<Arguments> candidateOnlyEndpoints() {
    return List.of(
        Arguments.of(HttpMethod.GET, "/dmz/user/generate-upload-resume?file-name=cv.pdf", null),
        Arguments.of(HttpMethod.POST, "/dmz/user/confirm-upload-resume?file-id=file-1", null),
        Arguments.of(HttpMethod.GET, "/dmz/user/resume", null),
        Arguments.of(HttpMethod.GET, "/dmz/user/resume/languages", null),
        Arguments.of(HttpMethod.POST, "/dmz/user/resume",
            "{\"language\":\"EN\",\"resume\":{},\"fileId\":\"file-1\"}"),
        Arguments.of(HttpMethod.PUT, "/dmz/user/resume/" + RESUME_ID + "/alias",
            "{\"alias\":\"Backend roles\"}"),
        Arguments.of(HttpMethod.DELETE, "/dmz/user/resume/" + RESUME_ID, null),
        Arguments.of(HttpMethod.GET, "/dmz/user/application", null),
        Arguments.of(HttpMethod.GET, "/dmz/user/upskilling-suggestion/" + JOB_ID, null),
        Arguments.of(HttpMethod.POST, "/dmz/job/" + JOB_ID + "/apply",
            "{\"resumeId\":\"" + RESUME_ID + "\"}"),
        Arguments.of(HttpMethod.GET, "/dmz/job/" + JOB_ID + "/upskilling-suggestion", null));
  }

  static List<Arguments> companyOnlyEndpoints() {
    return List.of(
        Arguments.of(HttpMethod.POST, "/dmz/job",
            "{\"title\":\"Backend Engineer\",\"description\":\"Java\",\"workType\":\"REMOTE\","
                + "\"jobPostSource\":\"LYNQ\"}"),
        Arguments.of(HttpMethod.GET, "/dmz/job/mine", null),
        Arguments.of(HttpMethod.POST, "/dmz/company",
            "{\"fullName\":\"Jane Doe\",\"currentPosition\":\"CTO\",\"userAbout\":\"Hiring\","
                + "\"birthDate\":\"1995-04-12\",\"companyName\":\"Lynq\","
                + "\"companyAbout\":\"Hiring platform\",\"companySize\":42}"));
  }

  @ParameterizedTest
  @MethodSource("candidateOnlyEndpoints")
  void candidateOnlyEndpointsAnswerForbiddenToACompany(HttpMethod method, String path, String body)
      throws Exception {
    mockMvc.perform(as(Role.COMPANY, method, path, body))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.success", is(false)))
        .andExpect(jsonPath("$.reason", is(ONLY_CANDIDATES)));
  }

  @ParameterizedTest
  @MethodSource("companyOnlyEndpoints")
  void companyOnlyEndpointsAnswerForbiddenToACandidate(HttpMethod method, String path, String body)
      throws Exception {
    mockMvc.perform(as(Role.CANDIDATE, method, path, body))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.success", is(false)))
        .andExpect(jsonPath("$.reason", is(ONLY_COMPANIES)));
  }

  @ParameterizedTest
  @MethodSource("candidateOnlyEndpoints")
  void candidateOnlyEndpointsAnswerForbiddenToACallerWithoutAnyRole(HttpMethod method, String path,
      String body) throws Exception {
    mockMvc.perform(as(null, method, path, body))
        .andExpect(status().isForbidden());
  }

  @Test
  void aCandidateReachesTheirOwnResumes() throws Exception {
    when(userService.getUserResumes(USER_ID)).thenReturn(List.of());

    mockMvc.perform(as(Role.CANDIDATE, HttpMethod.GET, "/dmz/user/resume", null))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success", is(true)));
  }

  @Test
  void aCandidateReachesTheirOwnApplications() throws Exception {
    when(userService.getUserApplications(any(), any()))
        .thenReturn(PagedRestResponse.from(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0)));

    mockMvc.perform(as(Role.CANDIDATE, HttpMethod.GET, "/dmz/user/application", null))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success", is(true)));
  }

  @Test
  void aCompanyReachesItsOwnJobs() throws Exception {
    when(jobService.searchOwnedJobs(any()))
        .thenReturn(PagedRestResponse.from(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0)));

    mockMvc.perform(as(Role.COMPANY, HttpMethod.GET, "/dmz/job/mine", null))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success", is(true)));
  }

  private MockHttpServletRequestBuilder as(String role, HttpMethod method, String path,
      String body) {
    MockHttpServletRequestBuilder builder = request(method, path)
        .header(REQUEST_UUID_HEADER, REQUEST_UUID)
        .with(authentication(authenticationWith(role)));

    if (body != null) {
      builder.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    return builder;
  }

  private static UsernamePasswordAuthenticationToken authenticationWith(String role) {
    List<GrantedAuthority> authorities = role == null
        ? List.of()
        : List.of(new SimpleGrantedAuthority(Role.PREFIX + role));
    LynqUserPrincipal principal = new LynqUserPrincipal(USER_ID, USERNAME, EMAIL, authorities);
    return new UsernamePasswordAuthenticationToken(principal, null, authorities);
  }
}
