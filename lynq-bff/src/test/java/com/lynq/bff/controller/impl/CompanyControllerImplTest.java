package com.lynq.bff.controller.impl;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lynq.bff.client.request.CreateUserWithCompanyRequest;
import com.lynq.bff.client.request.UpdateCompanyRequest;
import com.lynq.bff.client.response.CreateUserWithCompanyResponse;
import com.lynq.bff.client.response.GenerateUploadImageResponse;
import com.lynq.bff.client.response.GetCompanyDetailResponse;
import com.lynq.bff.client.response.UpdateCompanyResponse;
import com.lynq.bff.controller.response.GlobalRestResponse;
import com.lynq.bff.service.Caller;
import com.lynq.bff.service.CompanyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class CompanyControllerImplTest {

  private static final String USER_ID = "11111111-1111-1111-1111-111111111111";
  private static final String REQUEST_UUID = "018f9c3a-2b1d-7c4e-9a6f-1e2d3c4b5a99";
  private static final String AUTHORIZATION = "Bearer access-token";
  private static final String COMPANY_ID = "018f9c3a-2b1d-7c4e-9a6f-1e2d3c4b5a62";
  private static final String FILE_ID = "0195f2c1-3b1a-7c2d-9f31-3f6a5f2c9d41";
  private static final String FILE_NAME = "logo.png";

  @Mock
  private CompanyService companyService;

  private CompanyControllerImpl companyController;

  @BeforeEach
  void setUp() {
    companyController = new CompanyControllerImpl(companyService);
  }

  @Test
  void answersACreatedCompanyWithCreated() {
    CreateUserWithCompanyRequest request =
        CreateUserWithCompanyRequest.builder().companyName("Lynq").build();
    CreateUserWithCompanyResponse created =
        CreateUserWithCompanyResponse.builder().companyId(COMPANY_ID).build();
    when(companyService.createUserWithCompany(eq(request), any())).thenReturn(created);

    ResponseEntity<GlobalRestResponse<CreateUserWithCompanyResponse>> response =
        companyController.createUserWithCompany(request, REQUEST_UUID, AUTHORIZATION, USER_ID);

    assertThat(response.getStatusCode(), is(HttpStatus.CREATED));
    assertThat(response.getBody().getData(), is(sameInstance(created)));
  }

  @Test
  void callsTheServiceAsTheVerifiedCaller() {
    CreateUserWithCompanyRequest request =
        CreateUserWithCompanyRequest.builder().companyName("Lynq").build();

    companyController.createUserWithCompany(request, REQUEST_UUID, AUTHORIZATION, USER_ID);

    ArgumentCaptor<Caller> caller = ArgumentCaptor.forClass(Caller.class);
    verify(companyService).createUserWithCompany(eq(request), caller.capture());
    assertThat(caller.getValue().userId(), is(USER_ID));
    assertThat(caller.getValue().requestUuid(), is(REQUEST_UUID));
    assertThat(caller.getValue().authorization(), is(AUTHORIZATION));
  }

  @Test
  void answersAnUpdatedCompanyWithOk() {
    UpdateCompanyRequest request = UpdateCompanyRequest.builder().size(40).build();
    UpdateCompanyResponse updated = UpdateCompanyResponse.builder().id(COMPANY_ID).build();
    when(companyService.updateCompany(eq(request), any())).thenReturn(updated);

    ResponseEntity<GlobalRestResponse<UpdateCompanyResponse>> response =
        companyController.updateCompany(request, REQUEST_UUID, AUTHORIZATION, USER_ID);

    assertThat(response.getStatusCode(), is(HttpStatus.OK));
    assertThat(response.getBody().getData(), is(sameInstance(updated)));
  }

  @Test
  void answersTheImageUploadUrlWithOk() {
    GenerateUploadImageResponse upload =
        GenerateUploadImageResponse.builder().fileId(FILE_ID).build();
    when(companyService.generateImageUploadUrl(eq(FILE_NAME), any())).thenReturn(upload);

    ResponseEntity<GlobalRestResponse<GenerateUploadImageResponse>> response =
        companyController.generateImageUploadUrl(FILE_NAME, REQUEST_UUID, AUTHORIZATION, USER_ID);

    assertThat(response.getStatusCode(), is(HttpStatus.OK));
    assertThat(response.getBody().getData(), is(sameInstance(upload)));
  }

  @Test
  void answersAConfirmedImageUploadWithNoContentAndNoBody() {
    ResponseEntity<Void> response =
        companyController.confirmImageUpload(FILE_ID, REQUEST_UUID, AUTHORIZATION, USER_ID);

    assertThat(response.getStatusCode(), is(HttpStatus.NO_CONTENT));
    assertThat(response.getBody(), is(nullValue()));
    verify(companyService).confirmImageUpload(eq(FILE_ID), any());
  }

  @Test
  void answersTheCompanyDetailWithOk() {
    GetCompanyDetailResponse company = GetCompanyDetailResponse.builder().id(COMPANY_ID).build();
    when(companyService.getCompanyDetail(eq(COMPANY_ID), any())).thenReturn(company);

    ResponseEntity<GlobalRestResponse<GetCompanyDetailResponse>> response =
        companyController.getCompanyDetail(COMPANY_ID, REQUEST_UUID, AUTHORIZATION, USER_ID);

    assertThat(response.getStatusCode(), is(HttpStatus.OK));
    assertThat(response.getBody().getData(), is(sameInstance(company)));
  }
}
