package com.lynq.bff.security;

import com.lynq.bff.client.LynqBackendDmzClient;
import com.lynq.bff.client.LynqFileStorageDmzClient;
import com.lynq.bff.client.LynqMlDmzClient;
import com.lynq.bff.config.SecurityConfig;
import com.lynq.bff.controller.handler.ControllerExceptionHandler;
import com.lynq.bff.controller.impl.DmzProxyControllerImpl;
import com.lynq.bff.controller.impl.ResumeControllerImpl;
import com.lynq.bff.controller.response.ResumePreviewRestResponse;
import com.lynq.bff.filter.JwtSignatureFilter;
import com.lynq.bff.service.DmzProxyService;
import com.lynq.bff.service.ResumeAliasService;
import com.lynq.bff.service.ResumeDeletionService;
import com.lynq.bff.service.ResumeImportService;
import com.lynq.bff.service.ResumePreviewService;
import com.lynq.bff.service.ResumeTranslationService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
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

@WebMvcTest(controllers = {ResumeControllerImpl.class, DmzProxyControllerImpl.class})
@Import({SecurityConfig.class, ControllerExceptionHandler.class})
class HasRoleAuthorizationTest {

  private static final String USER_ID = "11111111-1111-1111-1111-111111111111";
  private static final String REQUEST_UUID_HEADER = "lynq-request-uuid";
  private static final String REQUEST_UUID = "018f9c3a-2b1d-7c4e-9a6f-1e2d3c4b5a99";
  private static final String AUTHORIZATION_HEADER = "Authorization";
  private static final String AUTHORIZATION = "Bearer access-token";
  private static final String RESUME_ID = "018f9c3a-2b1d-7c4e-9a6f-1e2d3c4b5a60";
  private static final String FILE_ID = "0195f2c1-3b1a-7c2d-9f31-3f6a5f2c9d41";

  private static final String ONLY_CANDIDATES =
      "Only users of type CANDIDATE can perform this action";

  @Autowired
  private MockMvc mockMvc;

  @MockitoBean
  private ResumePreviewService resumePreviewService;

  @MockitoBean
  private ResumeImportService resumeImportService;

  @MockitoBean
  private ResumeDeletionService resumeDeletionService;

  @MockitoBean
  private ResumeTranslationService resumeTranslationService;

  @MockitoBean
  private ResumeAliasService resumeAliasService;

  @MockitoBean
  private DmzProxyService dmzProxyService;

  @MockitoBean
  private LynqBackendDmzClient lynqBackendDmzClient;

  @MockitoBean
  private LynqMlDmzClient lynqMlDmzClient;

  @MockitoBean
  private LynqFileStorageDmzClient lynqFileStorageDmzClient;

  static List<Arguments> resumeEndpoints() {
    return List.of(
        Arguments.of(HttpMethod.POST, "/resume/preview",
            "{\"resume\":{},\"template\":\"CLASSIC\"}"),
        Arguments.of(HttpMethod.POST, "/resume/document/" + FILE_ID + "/import", null),
        Arguments.of(HttpMethod.POST, "/resume/" + RESUME_ID + "/translate",
            "{\"language\":\"FR\"}"),
        Arguments.of(HttpMethod.DELETE, "/resume/preview/" + FILE_ID, null),
        Arguments.of(HttpMethod.PUT, "/resume/" + RESUME_ID + "/alias",
            "{\"alias\":\"Backend roles\"}"),
        Arguments.of(HttpMethod.DELETE, "/resume/" + RESUME_ID, null));
  }

  @ParameterizedTest
  @MethodSource("resumeEndpoints")
  void resumeEndpointsAnswerForbiddenToACompany(HttpMethod method, String path, String body)
      throws Exception {
    mockMvc.perform(as(Role.COMPANY, method, path, body))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.success", is(false)))
        .andExpect(jsonPath("$.reason", is(ONLY_CANDIDATES)));
  }

  @ParameterizedTest
  @MethodSource("resumeEndpoints")
  void resumeEndpointsAnswerForbiddenToACallerWithoutAnyRole(HttpMethod method, String path,
      String body) throws Exception {
    mockMvc.perform(as(null, method, path, body))
        .andExpect(status().isForbidden());
  }

  @Test
  void aCandidateReachesThePreviewFlow() throws Exception {
    when(resumePreviewService.preview(any(), any()))
        .thenReturn(ResumePreviewRestResponse.builder().fileId(FILE_ID).build());

    mockMvc.perform(as(Role.CANDIDATE, HttpMethod.POST, "/resume/preview",
            "{\"resume\":{},\"template\":\"CLASSIC\"}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.success", is(true)))
        .andExpect(jsonPath("$.data.fileId", is(FILE_ID)));
  }

  @Test
  void aCandidateReachesTheResumeDeletion() throws Exception {
    mockMvc.perform(as(Role.CANDIDATE, HttpMethod.DELETE, "/resume/" + RESUME_ID, null))
        .andExpect(status().isNoContent());
  }

  @Test
  void theRelayedRoutesStayReachableWhateverRoleTheCallerHolds() throws Exception {
    when(dmzProxyService.forward(any(), any(), any()))
        .thenReturn(ResponseEntity.ok(new byte[0]));

    mockMvc.perform(as(null, HttpMethod.GET, "/user", null))
        .andExpect(status().isOk());
  }

  private MockHttpServletRequestBuilder as(String role, HttpMethod method, String path,
      String body) {
    MockHttpServletRequestBuilder builder = request(method, path)
        .header(REQUEST_UUID_HEADER, REQUEST_UUID)
        .header(AUTHORIZATION_HEADER, AUTHORIZATION)
        .requestAttr(JwtSignatureFilter.VERIFIED_USER_ID, USER_ID)
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
    return new UsernamePasswordAuthenticationToken(USER_ID, null, authorities);
  }
}
