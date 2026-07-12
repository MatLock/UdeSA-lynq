package com.lynq.backend.service;

import com.lynq.backend.client.LynqMLClient;
import com.lynq.backend.client.request.SkillEnhanceRequest;
import com.lynq.backend.client.response.SkillEnhanceResponse;
import com.lynq.backend.controller.response.GlobalRestResponse;
import com.lynq.backend.enums.UserType;
import com.lynq.backend.enums.WorkType;
import com.lynq.backend.exceptions.BadRequestException;
import com.lynq.backend.model.CompanyEntity;
import com.lynq.backend.model.UserEntity;
import com.lynq.backend.repository.CompanyRepository;
import com.lynq.backend.repository.UserRepository;
import com.lynq.backend.security.LynqUserPrincipal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LynqMLProxyServiceTest {

  private static final String USER_ID = "550e8400-e29b-41d4-a716-446655440000";
  private static final String USERNAME = "janedoe";
  private static final String EMAIL = "jane@lynq.com";
  private static final String COMPANY_ID = "company-1";
  private static final String REQUEST_UUID = "11111111-1111-1111-1111-111111111111";

  private static final String TITLE = "Senior Backend Engineer";
  private static final String DESCRIPTION = "Build and scale the Lynq hiring platform.";
  private static final WorkType WORK_TYPE = WorkType.REMOTE;

  private static final String SKILL_JAVA = "Java";
  private static final String SKILL_SPRING = "Spring";
  private static final List<String> SKILLS = List.of(SKILL_JAVA, SKILL_SPRING);

  private static final String ONLY_COMPANY_USERS_CAN_ENHANCE_SKILLS =
      "Only users of type COMPANY can enhance skills";
  private static final String USER_NOT_LINKED_TO_COMPANY = "User is not linked to any company";
  private static final String AUTHENTICATED_USER_NOT_FOUND = "Authenticated user not found";

  @Mock
  private LynqMLClient lynqMLClient;

  @Mock
  private UserRepository userRepository;

  @Mock
  private CompanyRepository companyRepository;

  @Mock
  private SecurityContext securityContext;

  @Mock
  private Authentication authentication;

  private LynqMLProxyService lynqMLProxyService;

  @BeforeEach
  void setUp() {
    lynqMLProxyService = new LynqMLProxyService(lynqMLClient, userRepository, companyRepository);
    SecurityContextHolder.setContext(securityContext);
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void enhanceSkillsForwardsRequestAndHeadersToClient() {
    UserEntity user = companyUser();
    stubAuthenticatedCompanyUserWithCompany(user);
    when(lynqMLClient.enhanceSkills(any(SkillEnhanceRequest.class), eq(REQUEST_UUID), eq(USER_ID),
        eq(COMPANY_ID))).thenReturn(new GlobalRestResponse<>(true, response()));
    ArgumentCaptor<SkillEnhanceRequest> requestCaptor =
        ArgumentCaptor.forClass(SkillEnhanceRequest.class);

    lynqMLProxyService.enhanceSkills(TITLE, DESCRIPTION, WORK_TYPE, REQUEST_UUID);

    verify(lynqMLClient).enhanceSkills(requestCaptor.capture(), eq(REQUEST_UUID), eq(USER_ID),
        eq(COMPANY_ID));
    SkillEnhanceRequest forwarded = requestCaptor.getValue();
    assertThat(forwarded.getTitle(), is(TITLE));
    assertThat(forwarded.getDescription(), is(DESCRIPTION));
    assertThat(forwarded.getWorkType(), is(WORK_TYPE));
  }

  @Test
  void enhanceSkillsReturnsSkillsFromClientResponse() {
    stubAuthenticatedCompanyUserWithCompany(companyUser());
    SkillEnhanceResponse mlResponse = response();
    when(lynqMLClient.enhanceSkills(any(SkillEnhanceRequest.class), eq(REQUEST_UUID), eq(USER_ID),
        eq(COMPANY_ID))).thenReturn(new GlobalRestResponse<>(true, mlResponse));

    SkillEnhanceResponse result =
        lynqMLProxyService.enhanceSkills(TITLE, DESCRIPTION, WORK_TYPE, REQUEST_UUID);

    assertThat(result, is(sameInstance(mlResponse)));
    assertThat(result.getSkills(), contains(SKILL_JAVA, SKILL_SPRING));
  }

  @Test
  void enhanceSkillsThrowsBadRequestWhenAuthenticatedUserNotFound() {
    stubAuthenticatedPrincipal();
    when(userRepository.findById(USER_ID)).thenReturn(Optional.empty());

    BadRequestException exception = assertThrows(BadRequestException.class,
        () -> lynqMLProxyService.enhanceSkills(TITLE, DESCRIPTION, WORK_TYPE, REQUEST_UUID));
    assertThat(exception.getMessage(), is(AUTHENTICATED_USER_NOT_FOUND));
    verify(lynqMLClient, never()).enhanceSkills(any(), any(), any(), any());
  }

  @Test
  void enhanceSkillsThrowsBadRequestWhenUserIsNotCompanyType() {
    UserEntity candidate = UserEntity.builder().id(USER_ID).type(UserType.CANDIDATE).build();
    stubAuthenticatedPrincipal();
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(candidate));

    BadRequestException exception = assertThrows(BadRequestException.class,
        () -> lynqMLProxyService.enhanceSkills(TITLE, DESCRIPTION, WORK_TYPE, REQUEST_UUID));
    assertThat(exception.getMessage(), is(ONLY_COMPANY_USERS_CAN_ENHANCE_SKILLS));
    verify(lynqMLClient, never()).enhanceSkills(any(), any(), any(), any());
  }

  @Test
  void enhanceSkillsThrowsBadRequestWhenUserNotLinkedToCompany() {
    UserEntity user = companyUser();
    stubAuthenticatedPrincipal();
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
    when(companyRepository.findByOwner(user)).thenReturn(Optional.empty());

    BadRequestException exception = assertThrows(BadRequestException.class,
        () -> lynqMLProxyService.enhanceSkills(TITLE, DESCRIPTION, WORK_TYPE, REQUEST_UUID));
    assertThat(exception.getMessage(), is(USER_NOT_LINKED_TO_COMPANY));
    verify(lynqMLClient, never()).enhanceSkills(any(), any(), any(), any());
  }

  private SkillEnhanceResponse response() {
    return SkillEnhanceResponse.builder().skills(SKILLS).build();
  }

  private UserEntity companyUser() {
    return UserEntity.builder().id(USER_ID).type(UserType.COMPANY).build();
  }

  private void stubAuthenticatedPrincipal() {
    when(securityContext.getAuthentication()).thenReturn(authentication);
    when(authentication.getPrincipal())
        .thenReturn(new LynqUserPrincipal(USER_ID, USERNAME, EMAIL));
  }

  private CompanyEntity stubAuthenticatedCompanyUserWithCompany(UserEntity user) {
    CompanyEntity company = CompanyEntity.builder().id(COMPANY_ID).owner(user).build();
    stubAuthenticatedPrincipal();
    when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
    when(companyRepository.findByOwner(user)).thenReturn(Optional.of(company));
    return company;
  }
}
