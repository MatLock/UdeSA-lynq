package com.lynq.backend.controller.impl;

import com.lynq.backend.aspect.AuditLog;
import com.lynq.backend.controller.request.CreateUserWithCompanyRequest;
import com.lynq.backend.controller.response.CreateUserWithCompanyRestResponse;
import com.lynq.backend.controller.response.GenerateUploadImageRestResponse;
import com.lynq.backend.controller.response.GetCompanyDetailRestResponse;
import com.lynq.backend.controller.response.GlobalRestResponse;
import com.lynq.backend.model.CompanyEntity;
import com.lynq.backend.security.LynqUserPrincipal;
import com.lynq.backend.service.CompanyService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/company")
@Validated
public class CompanyControllerImpl implements com.lynq.backend.controller.CompanyController {

  private final CompanyService companyService;

  public CompanyControllerImpl(CompanyService companyService) {
    this.companyService = companyService;
  }

  @Override
  @PostMapping
  @AuditLog
  public ResponseEntity<GlobalRestResponse<CreateUserWithCompanyRestResponse>> createUserWithCompany(@RequestBody CreateUserWithCompanyRequest request, @AuthenticationPrincipal LynqUserPrincipal principal) {
    CompanyEntity company = companyService.createUserWithCompany(principal.getId(), request);

    CreateUserWithCompanyRestResponse response = CreateUserWithCompanyRestResponse.builder()
        .companyId(company.getId())
        .companyName(company.getName())
        .companyAbout(company.getAbout())
        .companySize(company.getSize())
        .companyProfileImageUrl(company.getProfileImageUrl())
        .companyCreatedOn(company.getCreatedOn())
        .ownerUserId(company.getOwner().getId())
        .build();

    return ResponseEntity
        .status(HttpStatus.CREATED)
        .body(new GlobalRestResponse<>(true, response));
  }

  @Override
  @GetMapping("/generate-upload-image")
  @AuditLog
  public ResponseEntity<GlobalRestResponse<GenerateUploadImageRestResponse>> generateCompanyImageUploadUrl(
      @RequestParam("file-name") String fileName, @AuthenticationPrincipal LynqUserPrincipal principal) {
    String preSignedUrl = companyService.generateCompanyImageUploadUrl(principal.getId(), fileName);

    GenerateUploadImageRestResponse response = GenerateUploadImageRestResponse.builder()
        .preSignedUrl(preSignedUrl)
        .build();

    return ResponseEntity
        .status(HttpStatus.OK)
        .body(new GlobalRestResponse<>(true, response));
  }

  @Override
  @GetMapping("/{companyId}")
  @AuditLog
  public ResponseEntity<GlobalRestResponse<GetCompanyDetailRestResponse>> getCompanyDetail(
      @PathVariable String companyId) {
    GetCompanyDetailRestResponse company = companyService.getCompanyDetail(companyId);

    return ResponseEntity
        .status(HttpStatus.OK)
        .body(new GlobalRestResponse<>(true, company));
  }

}