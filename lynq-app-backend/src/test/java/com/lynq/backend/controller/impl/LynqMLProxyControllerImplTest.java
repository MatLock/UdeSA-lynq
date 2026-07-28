package com.lynq.backend.controller.impl;

import com.lynq.backend.client.response.SkillEnhanceResponse;
import com.lynq.backend.client.response.SkillExtractionResponse;
import com.lynq.backend.controller.request.SkillEnhanceRequest;
import com.lynq.backend.controller.response.GlobalRestResponse;
import com.lynq.backend.enums.WorkType;
import com.lynq.backend.service.LynqMLProxyService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LynqMLProxyControllerImplTest {

  private static final String TITLE = "Senior Backend Engineer";
  private static final String DESCRIPTION = "Build and scale the Lynq hiring platform.";
  private static final WorkType WORK_TYPE = WorkType.REMOTE;
  private static final String REQUEST_UUID = "550e8400-e29b-41d4-a716-446655440000";
  private static final String SKILL_JAVA = "Java";
  private static final String SKILL_SPRING = "Spring";
  private static final List<String> SKILLS = List.of(SKILL_JAVA, SKILL_SPRING);

  private static final String TOOL_GIT = "Git";
  private static final String SOFT_TEAMWORK = "Teamwork";
  private static final Object RESUME_PAYLOAD = new Object();
  private static final String LANGUAGE = "es";

  @Mock
  private LynqMLProxyService lynqMLProxyService;

  @Mock
  private SkillEnhanceRequest request;

  private LynqMLProxyControllerImpl lynqMLProxyController;

  @BeforeEach
  void setUp() {
    lynqMLProxyController = new LynqMLProxyControllerImpl(lynqMLProxyService);
    lenient().when(request.getTitle()).thenReturn(TITLE);
    lenient().when(request.getDescription()).thenReturn(DESCRIPTION);
    lenient().when(request.getWorkType()).thenReturn(WORK_TYPE);
    lenient().when(lynqMLProxyService.enhanceSkills(TITLE, DESCRIPTION, WORK_TYPE, REQUEST_UUID))
        .thenReturn(response());
    lenient().when(lynqMLProxyService.extractResumeSkills(RESUME_PAYLOAD, REQUEST_UUID, LANGUAGE))
        .thenReturn(extractionResponse());
  }

  @Test
  void enhanceSkillsDelegatesToServiceWithRequestFieldsAndRequestUuid() {
    lynqMLProxyController.enhanceSkills(request, REQUEST_UUID);

    verify(lynqMLProxyService).enhanceSkills(TITLE, DESCRIPTION, WORK_TYPE, REQUEST_UUID);
  }

  @Test
  void enhanceSkillsRespondsWithOkStatus() {
    ResponseEntity<GlobalRestResponse<SkillEnhanceResponse>> response =
        lynqMLProxyController.enhanceSkills(request, REQUEST_UUID);

    assertThat(response.getStatusCode(), is(HttpStatus.OK));
  }

  @Test
  void enhanceSkillsWrapsSuccessfulResponseBody() {
    ResponseEntity<GlobalRestResponse<SkillEnhanceResponse>> response =
        lynqMLProxyController.enhanceSkills(request, REQUEST_UUID);

    GlobalRestResponse<SkillEnhanceResponse> body = response.getBody();
    assertThat(body, is(notNullValue()));
    assertThat(body.isSuccess(), is(true));
  }

  @Test
  void enhanceSkillsMapsServiceResponseIntoData() {
    ResponseEntity<GlobalRestResponse<SkillEnhanceResponse>> response =
        lynqMLProxyController.enhanceSkills(request, REQUEST_UUID);

    SkillEnhanceResponse data = response.getBody().getData();
    assertThat(data.getSkills(), contains(SKILL_JAVA, SKILL_SPRING));
  }

  @Test
  void extractResumeSkillsDelegatesToServiceWithPayloadRequestUuidAndLanguage() {
    lynqMLProxyController.extractResumeSkills(RESUME_PAYLOAD, REQUEST_UUID, LANGUAGE);

    verify(lynqMLProxyService).extractResumeSkills(RESUME_PAYLOAD, REQUEST_UUID, LANGUAGE);
  }

  @Test
  void extractResumeSkillsRespondsWithOkStatus() {
    ResponseEntity<GlobalRestResponse<SkillExtractionResponse>> response =
        lynqMLProxyController.extractResumeSkills(RESUME_PAYLOAD, REQUEST_UUID, LANGUAGE);

    assertThat(response.getStatusCode(), is(HttpStatus.OK));
  }

  @Test
  void extractResumeSkillsWrapsSuccessfulResponseBody() {
    ResponseEntity<GlobalRestResponse<SkillExtractionResponse>> response =
        lynqMLProxyController.extractResumeSkills(RESUME_PAYLOAD, REQUEST_UUID, LANGUAGE);

    GlobalRestResponse<SkillExtractionResponse> body = response.getBody();
    assertThat(body, is(notNullValue()));
    assertThat(body.isSuccess(), is(true));
  }

  @Test
  void extractResumeSkillsMapsServiceResponseIntoData() {
    ResponseEntity<GlobalRestResponse<SkillExtractionResponse>> response =
        lynqMLProxyController.extractResumeSkills(RESUME_PAYLOAD, REQUEST_UUID, LANGUAGE);

    SkillExtractionResponse data = response.getBody().getData();
    assertThat(data.getSkills(), contains(SKILL_JAVA, SKILL_SPRING));
    assertThat(data.getTools(), contains(TOOL_GIT));
    assertThat(data.getSoft(), contains(SOFT_TEAMWORK));
  }

  private SkillEnhanceResponse response() {
    return SkillEnhanceResponse.builder().skills(SKILLS).build();
  }

  private SkillExtractionResponse extractionResponse() {
    return SkillExtractionResponse.builder()
        .skills(SKILLS)
        .tools(List.of(TOOL_GIT))
        .soft(List.of(SOFT_TEAMWORK))
        .build();
  }
}
