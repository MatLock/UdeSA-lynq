package com.lynq.bff.service;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lynq.bff.client.LynqBackendClient;
import com.lynq.bff.client.request.CreateUserWithCompanyRequest;
import com.lynq.bff.client.request.UpdateCompanyRequest;
import com.lynq.bff.client.response.CreateUserWithCompanyResponse;
import com.lynq.bff.client.response.GenerateUploadImageResponse;
import com.lynq.bff.client.response.GetCompanyDetailResponse;
import com.lynq.bff.client.response.UpdateCompanyResponse;
import com.lynq.bff.controller.response.GlobalRestResponse;
import com.lynq.bff.exceptions.ConflictException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CompanyServiceTest {

  private static final String USER_ID = "11111111-1111-1111-1111-111111111111";
  private static final String REQUEST_UUID = "018f9c3a-2b1d-7c4e-9a6f-1e2d3c4b5a99";
  private static final String AUTHORIZATION = "Bearer access-token";
  private static final Caller CALLER = new Caller(USER_ID, REQUEST_UUID, AUTHORIZATION);

  private static final String COMPANY_ID = "018f9c3a-2b1d-7c4e-9a6f-1e2d3c4b5a62";
  private static final String FILE_ID = "0195f2c1-3b1a-7c2d-9f31-3f6a5f2c9d41";
  private static final String FILE_NAME = "logo.png";

  @Mock
  private LynqBackendClient lynqBackendClient;

  private CompanyService companyService;

  @BeforeEach
  void setUp() {
    companyService = new CompanyService(lynqBackendClient);
  }

  @Test
  void createsTheCompanyForTheAuthenticatedUser() {
    CreateUserWithCompanyRequest request =
        CreateUserWithCompanyRequest.builder().companyName("Lynq").build();
    CreateUserWithCompanyResponse created =
        CreateUserWithCompanyResponse.builder().companyId(COMPANY_ID).build();
    when(lynqBackendClient.createUserWithCompany(request, REQUEST_UUID, AUTHORIZATION))
        .thenReturn(new GlobalRestResponse<>(true, created));

    assertThat(companyService.createUserWithCompany(request, CALLER), is(sameInstance(created)));
  }

  @Test
  void updatesTheCompany() {
    UpdateCompanyRequest request = UpdateCompanyRequest.builder().size(40).build();
    UpdateCompanyResponse updated = UpdateCompanyResponse.builder().id(COMPANY_ID).build();
    when(lynqBackendClient.updateCompany(request, REQUEST_UUID, AUTHORIZATION))
        .thenReturn(new GlobalRestResponse<>(true, updated));

    assertThat(companyService.updateCompany(request, CALLER), is(sameInstance(updated)));
  }

  @Test
  void issuesTheCompanyImageUploadUrl() {
    GenerateUploadImageResponse upload =
        GenerateUploadImageResponse.builder().fileId(FILE_ID).build();
    when(lynqBackendClient.generateCompanyImageUploadUrl(FILE_NAME, REQUEST_UUID, AUTHORIZATION))
        .thenReturn(new GlobalRestResponse<>(true, upload));

    assertThat(companyService.generateImageUploadUrl(FILE_NAME, CALLER), is(sameInstance(upload)));
  }

  @Test
  void confirmsTheCompanyImageUpload() {
    companyService.confirmImageUpload(FILE_ID, CALLER);

    verify(lynqBackendClient).confirmCompanyImageUpload(FILE_ID, REQUEST_UUID, AUTHORIZATION);
  }

  @Test
  void readsTheCompanyDetail() {
    GetCompanyDetailResponse company = GetCompanyDetailResponse.builder().id(COMPANY_ID).build();
    when(lynqBackendClient.getCompanyDetail(COMPANY_ID, REQUEST_UUID, AUTHORIZATION))
        .thenReturn(new GlobalRestResponse<>(true, company));

    assertThat(companyService.getCompanyDetail(COMPANY_ID, CALLER), is(sameInstance(company)));
  }

  @Test
  void keepsTheConflictAndItsCodeWhenTheCompanyAlreadyExists() {
    CreateUserWithCompanyRequest request =
        CreateUserWithCompanyRequest.builder().companyName("Lynq").build();
    when(lynqBackendClient.createUserWithCompany(request, REQUEST_UUID, AUTHORIZATION))
        .thenThrow(FeignErrors.status(409, """
            {"success": false, "reason": "The user already owns a company", \
"code": "COMPANY_ALREADY_OWNED"}"""));

    ConflictException thrown = assertThrows(ConflictException.class,
        () -> companyService.createUserWithCompany(request, CALLER));

    assertThat(thrown.getMessage(), is("The user already owns a company"));
    assertThat(thrown.getCode(), is("COMPANY_ALREADY_OWNED"));
  }
}
