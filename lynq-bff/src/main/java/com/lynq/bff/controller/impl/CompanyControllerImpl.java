package com.lynq.bff.controller.impl;

import com.lynq.bff.client.request.CreateUserWithCompanyRequest;
import com.lynq.bff.client.request.UpdateCompanyRequest;
import com.lynq.bff.client.response.CreateUserWithCompanyResponse;
import com.lynq.bff.client.response.GenerateUploadImageResponse;
import com.lynq.bff.client.response.GetCompanyDetailResponse;
import com.lynq.bff.client.response.UpdateCompanyResponse;
import com.lynq.bff.controller.CompanyController;
import com.lynq.bff.controller.response.GlobalRestResponse;
import com.lynq.bff.security.LynqUserPrincipal;
import com.lynq.bff.service.Caller;
import com.lynq.bff.service.CompanyService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/company")
public class CompanyControllerImpl implements CompanyController {

  private static final String REQUEST_UUID_HEADER = "lynq-request-uuid";

  private final CompanyService companyService;

  public CompanyControllerImpl(CompanyService companyService) {
    this.companyService = companyService;
  }

  @Override
  @PostMapping
  public ResponseEntity<GlobalRestResponse<CreateUserWithCompanyResponse>> createUserWithCompany(
      @RequestBody CreateUserWithCompanyRequest request,
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid,
      @AuthenticationPrincipal LynqUserPrincipal principal) {
    CreateUserWithCompanyResponse created = companyService.createUserWithCompany(
        request, caller(principal, requestUuid));

    return ResponseEntity
        .status(HttpStatus.CREATED)
        .body(new GlobalRestResponse<>(true, created));
  }

  @Override
  @PatchMapping
  public ResponseEntity<GlobalRestResponse<UpdateCompanyResponse>> updateCompany(
      @RequestBody UpdateCompanyRequest request,
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid,
      @AuthenticationPrincipal LynqUserPrincipal principal) {
    UpdateCompanyResponse updated = companyService.updateCompany(
        request, caller(principal, requestUuid));

    return ResponseEntity
        .status(HttpStatus.OK)
        .body(new GlobalRestResponse<>(true, updated));
  }

  @Override
  @GetMapping("/generate-upload-image")
  public ResponseEntity<GlobalRestResponse<GenerateUploadImageResponse>> generateImageUploadUrl(
      @RequestParam("file-name") String fileName,
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid,
      @AuthenticationPrincipal LynqUserPrincipal principal) {
    GenerateUploadImageResponse upload = companyService.generateImageUploadUrl(
        fileName, caller(principal, requestUuid));

    return ResponseEntity
        .status(HttpStatus.OK)
        .body(new GlobalRestResponse<>(true, upload));
  }

  @Override
  @PostMapping("/confirm-upload-image")
  public ResponseEntity<Void> confirmImageUpload(
      @RequestParam("file-id") String fileId,
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid,
      @AuthenticationPrincipal LynqUserPrincipal principal) {
    companyService.confirmImageUpload(fileId, caller(principal, requestUuid));

    return ResponseEntity.noContent().build();
  }

  @Override
  @GetMapping("/{companyId}")
  public ResponseEntity<GlobalRestResponse<GetCompanyDetailResponse>> getCompanyDetail(
      @PathVariable String companyId,
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid,
      @AuthenticationPrincipal LynqUserPrincipal principal) {
    GetCompanyDetailResponse company = companyService.getCompanyDetail(
        companyId, caller(principal, requestUuid));

    return ResponseEntity
        .status(HttpStatus.OK)
        .body(new GlobalRestResponse<>(true, company));
  }

  private static Caller caller(LynqUserPrincipal principal, String requestUuid) {
    return new Caller(principal.getId(), requestUuid, principal.getAuthorization());
  }
}
