package com.lynq.backend.controller.impl;

import com.lynq.backend.controller.request.CreateUserWithCompanyRequest;
import com.lynq.backend.controller.request.UpdateCompanyRequest;
import com.lynq.backend.controller.response.CreateUserWithCompanyRestResponse;
import com.lynq.backend.controller.response.GenerateUploadImageRestResponse;
import com.lynq.backend.controller.response.GetCompanyDetailRestResponse;
import com.lynq.backend.controller.response.GlobalRestResponse;
import com.lynq.backend.controller.response.UpdateCompanyRestResponse;
import com.lynq.backend.model.CompanyEntity;
import com.lynq.backend.model.UserEntity;
import com.lynq.backend.security.LynqUserPrincipal;
import com.lynq.backend.service.CompanyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompanyControllerImplTest {

  private static final String USER_ID = "550e8400-e29b-41d4-a716-446655440000";
  private static final String COMPANY_ID = "018f9c3a-2b1d-7c4e-9a6f-1e2d3c4b5a60";
  private static final String COMPANY_NAME = "Lynq Technologies";
  private static final String COMPANY_ABOUT = "We build talent matching platforms.";
  private static final Integer COMPANY_SIZE = 250;
  private static final String COMPANY_PROFILE_IMAGE_URL = "https://cdn.lynq.com/logos/lynq.png";
  private static final LocalDate CREATED_ON = LocalDate.of(2026, 6, 25);
  private static final String FILE_NAME = "logo.png";
  private static final String PRE_SIGNED_URL =
      "https://lynq-bucket.s3.amazonaws.com/logo.png?sig=abc";

  @Mock
  private CompanyService companyService;

  @Mock
  private CreateUserWithCompanyRequest request;

  @Mock
  private UpdateCompanyRequest updateRequest;

  @Mock
  private LynqUserPrincipal principal;

  private CompanyControllerImpl companyController;

  @BeforeEach
  void setUp() {
    companyController = new CompanyControllerImpl(companyService);
    lenient().when(principal.getId()).thenReturn(USER_ID);
    // Used by the create tests only; lenient so the logo-upload tests don't trip
    // strict stubbing.
    lenient().when(companyService.createUserWithCompany(USER_ID, request))
        .thenReturn(savedCompany());
  }

  @Test
  void createUserWithCompanyDelegatesToServiceWithPrincipalIdAndRequest() {
    companyController.createUserWithCompany(request, principal);

    verify(companyService).createUserWithCompany(USER_ID, request);
  }

  @Test
  void createUserWithCompanyRespondsWithCreatedStatus() {
    ResponseEntity<GlobalRestResponse<CreateUserWithCompanyRestResponse>> response =
        companyController.createUserWithCompany(request, principal);

    assertThat(response.getStatusCode(), is(HttpStatus.CREATED));
  }

  @Test
  void createUserWithCompanyWrapsSuccessfulResponseBody() {
    ResponseEntity<GlobalRestResponse<CreateUserWithCompanyRestResponse>> response =
        companyController.createUserWithCompany(request, principal);

    GlobalRestResponse<CreateUserWithCompanyRestResponse> body = response.getBody();
    assertThat(body, is(notNullValue()));
    assertThat(body.isSuccess(), is(true));
  }

  @Test
  void createUserWithCompanyMapsSavedCompanyIntoResponseData() {
    ResponseEntity<GlobalRestResponse<CreateUserWithCompanyRestResponse>> response =
        companyController.createUserWithCompany(request, principal);

    CreateUserWithCompanyRestResponse data = response.getBody().getData();
    assertThat(data.getCompanyId(), is(COMPANY_ID));
    assertThat(data.getCompanyName(), is(COMPANY_NAME));
    assertThat(data.getCompanyAbout(), is(COMPANY_ABOUT));
    assertThat(data.getCompanySize(), is(COMPANY_SIZE));
    assertThat(data.getCompanyProfileImageUrl(), is(COMPANY_PROFILE_IMAGE_URL));
    assertThat(data.getCompanyCreatedOn(), is(CREATED_ON));
    assertThat(data.getOwnerUserId(), is(USER_ID));
  }

  @Test
  void generateCompanyImageUploadUrlDelegatesToServiceWithPrincipalIdAndFileName() {
    when(companyService.generateCompanyImageUploadUrl(USER_ID, FILE_NAME)).thenReturn(PRE_SIGNED_URL);

    companyController.generateCompanyImageUploadUrl(FILE_NAME, principal);

    verify(companyService).generateCompanyImageUploadUrl(USER_ID, FILE_NAME);
  }

  @Test
  void generateCompanyImageUploadUrlRespondsWithOkStatusAndPreSignedUrl() {
    when(companyService.generateCompanyImageUploadUrl(USER_ID, FILE_NAME)).thenReturn(PRE_SIGNED_URL);

    ResponseEntity<GlobalRestResponse<GenerateUploadImageRestResponse>> response =
        companyController.generateCompanyImageUploadUrl(FILE_NAME, principal);

    assertThat(response.getStatusCode(), is(HttpStatus.OK));
    GlobalRestResponse<GenerateUploadImageRestResponse> body = response.getBody();
    assertThat(body, is(notNullValue()));
    assertThat(body.isSuccess(), is(true));
    assertThat(body.getData().getPreSignedUrl(), is(PRE_SIGNED_URL));
  }

  @Test
  void getCompanyDetailDelegatesToServiceWithPathCompanyId() {
    when(companyService.getCompanyDetail(COMPANY_ID))
        .thenReturn(GetCompanyDetailRestResponse.builder().id(COMPANY_ID).build());

    companyController.getCompanyDetail(COMPANY_ID);

    verify(companyService).getCompanyDetail(COMPANY_ID);
  }

  @Test
  void getCompanyDetailRespondsWithOkStatus() {
    when(companyService.getCompanyDetail(COMPANY_ID))
        .thenReturn(GetCompanyDetailRestResponse.builder().id(COMPANY_ID).build());

    ResponseEntity<GlobalRestResponse<GetCompanyDetailRestResponse>> response =
        companyController.getCompanyDetail(COMPANY_ID);

    assertThat(response.getStatusCode(), is(HttpStatus.OK));
  }

  @Test
  void getCompanyDetailWrapsServiceResultInSuccessfulEnvelope() {
    GetCompanyDetailRestResponse detail = GetCompanyDetailRestResponse.builder()
        .id(COMPANY_ID)
        .name(COMPANY_NAME)
        .build();
    when(companyService.getCompanyDetail(COMPANY_ID)).thenReturn(detail);

    ResponseEntity<GlobalRestResponse<GetCompanyDetailRestResponse>> response =
        companyController.getCompanyDetail(COMPANY_ID);

    GlobalRestResponse<GetCompanyDetailRestResponse> body = response.getBody();
    assertThat(body, is(notNullValue()));
    assertThat(body.isSuccess(), is(true));
    assertThat(body.getData().getId(), is(COMPANY_ID));
    assertThat(body.getData().getName(), is(COMPANY_NAME));
  }

  @Test
  void updateCompanyDelegatesToServiceWithPrincipalIdAndRequest() {
    when(companyService.updateCompany(USER_ID, updateRequest)).thenReturn(updatedCompany());

    companyController.updateCompany(updateRequest, principal);

    verify(companyService).updateCompany(USER_ID, updateRequest);
  }

  @Test
  void updateCompanyRespondsWithOkStatus() {
    when(companyService.updateCompany(USER_ID, updateRequest)).thenReturn(updatedCompany());

    ResponseEntity<GlobalRestResponse<UpdateCompanyRestResponse>> response =
        companyController.updateCompany(updateRequest, principal);

    assertThat(response.getStatusCode(), is(HttpStatus.OK));
  }

  @Test
  void updateCompanyWrapsServiceResultInSuccessfulEnvelope() {
    when(companyService.updateCompany(USER_ID, updateRequest)).thenReturn(updatedCompany());

    ResponseEntity<GlobalRestResponse<UpdateCompanyRestResponse>> response =
        companyController.updateCompany(updateRequest, principal);

    GlobalRestResponse<UpdateCompanyRestResponse> body = response.getBody();
    assertThat(body, is(notNullValue()));
    assertThat(body.isSuccess(), is(true));
    assertThat(body.getData().getId(), is(COMPANY_ID));
    assertThat(body.getData().getName(), is(COMPANY_NAME));
    assertThat(body.getData().getAbout(), is(COMPANY_ABOUT));
    assertThat(body.getData().getSize(), is(COMPANY_SIZE));
  }

  private UpdateCompanyRestResponse updatedCompany() {
    return UpdateCompanyRestResponse.builder()
        .id(COMPANY_ID)
        .name(COMPANY_NAME)
        .about(COMPANY_ABOUT)
        .size(COMPANY_SIZE)
        .profileImageUrl(COMPANY_PROFILE_IMAGE_URL)
        .createdOn(CREATED_ON)
        .build();
  }

  private CompanyEntity savedCompany() {
    return CompanyEntity.builder()
        .id(COMPANY_ID)
        .name(COMPANY_NAME)
        .about(COMPANY_ABOUT)
        .size(COMPANY_SIZE)
        .profileImageUrl(COMPANY_PROFILE_IMAGE_URL)
        .createdOn(CREATED_ON)
        .owner(UserEntity.builder().id(USER_ID).build())
        .build();
  }
}