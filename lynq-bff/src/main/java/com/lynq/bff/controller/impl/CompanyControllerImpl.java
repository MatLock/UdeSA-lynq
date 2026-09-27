package com.lynq.bff.controller.impl;

import com.lynq.bff.client.request.CreateUserWithCompanyRequest;
import com.lynq.bff.client.request.UpdateCompanyRequest;
import com.lynq.bff.client.response.CreateUserWithCompanyResponse;
import com.lynq.bff.client.response.GenerateUploadImageResponse;
import com.lynq.bff.client.response.GetCompanyDetailResponse;
import com.lynq.bff.client.response.UpdateCompanyResponse;
import com.lynq.bff.controller.CompanyController;
import com.lynq.bff.controller.response.GlobalRestResponse;
import com.lynq.bff.filter.JwtSignatureFilter;
import com.lynq.bff.service.Caller;
import com.lynq.bff.service.CompanyService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/company")
public class CompanyControllerImpl implements CompanyController {

  private static final String REQUEST_UUID_HEADER = "lynq-request-uuid";
  private static final String AUTHORIZATION_HEADER = "Authorization";

  private final CompanyService companyService;

  public CompanyControllerImpl(CompanyService companyService) {
    this.companyService = companyService;
  }

  @Override
  @PostMapping
  public ResponseEntity<GlobalRestResponse<CreateUserWithCompanyResponse>> createUserWithCompany(
      @RequestBody CreateUserWithCompanyRequest request,
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid,
      @RequestHeader(AUTHORIZATION_HEADER) String authorization,
      @RequestAttribute(JwtSignatureFilter.VERIFIED_USER_ID) String userId) {
    CreateUserWithCompanyResponse created = companyService.createUserWithCompany(
        request, new Caller(userId, requestUuid, authorization));

    return ResponseEntity
        .status(HttpStatus.CREATED)
        .body(new GlobalRestResponse<>(true, created));
  }

  @Override
  @PatchMapping
  public ResponseEntity<GlobalRestResponse<UpdateCompanyResponse>> updateCompany(
      @RequestBody UpdateCompanyRequest request,
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid,
      @RequestHeader(AUTHORIZATION_HEADER) String authorization,
      @RequestAttribute(JwtSignatureFilter.VERIFIED_USER_ID) String userId) {
    UpdateCompanyResponse updated = companyService.updateCompany(
        request, new Caller(userId, requestUuid, authorization));

    return ResponseEntity
        .status(HttpStatus.OK)
        .body(new GlobalRestResponse<>(true, updated));
  }

  @Override
  @GetMapping("/generate-upload-image")
  public ResponseEntity<GlobalRestResponse<GenerateUploadImageResponse>> generateImageUploadUrl(
      @RequestParam("file-name") String fileName,
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid,
      @RequestHeader(AUTHORIZATION_HEADER) String authorization,
      @RequestAttribute(JwtSignatureFilter.VERIFIED_USER_ID) String userId) {
    GenerateUploadImageResponse upload = companyService.generateImageUploadUrl(
        fileName, new Caller(userId, requestUuid, authorization));

    return ResponseEntity
        .status(HttpStatus.OK)
        .body(new GlobalRestResponse<>(true, upload));
  }

  @Override
  @PostMapping("/confirm-upload-image")
  public ResponseEntity<Void> confirmImageUpload(
      @RequestParam("file-id") String fileId,
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid,
      @RequestHeader(AUTHORIZATION_HEADER) String authorization,
      @RequestAttribute(JwtSignatureFilter.VERIFIED_USER_ID) String userId) {
    companyService.confirmImageUpload(fileId, new Caller(userId, requestUuid, authorization));

    return ResponseEntity.noContent().build();
  }

  @Override
  @GetMapping("/{companyId}")
  public ResponseEntity<GlobalRestResponse<GetCompanyDetailResponse>> getCompanyDetail(
      @PathVariable String companyId,
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid,
      @RequestHeader(AUTHORIZATION_HEADER) String authorization,
      @RequestAttribute(JwtSignatureFilter.VERIFIED_USER_ID) String userId) {
    GetCompanyDetailResponse company = companyService.getCompanyDetail(
        companyId, new Caller(userId, requestUuid, authorization));

    return ResponseEntity
        .status(HttpStatus.OK)
        .body(new GlobalRestResponse<>(true, company));
  }
}
