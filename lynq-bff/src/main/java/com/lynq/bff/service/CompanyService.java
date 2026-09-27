package com.lynq.bff.service;

import com.lynq.bff.client.LynqBackendClient;
import com.lynq.bff.client.request.CreateUserWithCompanyRequest;
import com.lynq.bff.client.request.UpdateCompanyRequest;
import com.lynq.bff.client.response.CreateUserWithCompanyResponse;
import com.lynq.bff.client.response.GenerateUploadImageResponse;
import com.lynq.bff.client.response.GetCompanyDetailResponse;
import com.lynq.bff.client.response.UpdateCompanyResponse;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

@Service
@Log4j2
public class CompanyService {

  private static final String COMPANY_NOT_CREATED = "The company could not be created";
  private static final String COMPANY_NOT_UPDATED = "The company could not be updated";
  private static final String IMAGE_URL_NOT_ISSUED = "The image upload url could not be issued";
  private static final String IMAGE_NOT_CONFIRMED = "The image upload could not be confirmed";
  private static final String COMPANY_UNREADABLE = "The company could not be read";

  private final LynqBackendClient lynqBackendClient;

  public CompanyService(LynqBackendClient lynqBackendClient) {
    this.lynqBackendClient = lynqBackendClient;
  }

  public CreateUserWithCompanyResponse createUserWithCompany(CreateUserWithCompanyRequest request,
                                                             Caller caller) {
    log.info("message= Creating company, user_id={}, company_name={}",
        caller.userId(), request.getCompanyName());

    return DownstreamErrors.call(
        () -> lynqBackendClient
            .createUserWithCompany(request, caller.requestUuid(), caller.authorization())
            .getData(),
        COMPANY_NOT_CREATED);
  }

  public UpdateCompanyResponse updateCompany(UpdateCompanyRequest request, Caller caller) {
    log.info("message= Updating company, user_id={}", caller.userId());

    return DownstreamErrors.call(
        () -> lynqBackendClient
            .updateCompany(request, caller.requestUuid(), caller.authorization())
            .getData(),
        COMPANY_NOT_UPDATED);
  }

  public GenerateUploadImageResponse generateImageUploadUrl(String fileName, Caller caller) {
    return DownstreamErrors.call(
        () -> lynqBackendClient
            .generateCompanyImageUploadUrl(fileName, caller.requestUuid(), caller.authorization())
            .getData(),
        IMAGE_URL_NOT_ISSUED);
  }

  public void confirmImageUpload(String fileId, Caller caller) {
    log.info("message= Confirming company image upload, user_id={}, file_id={}",
        caller.userId(), fileId);

    DownstreamErrors.run(
        () -> lynqBackendClient
            .confirmCompanyImageUpload(fileId, caller.requestUuid(), caller.authorization()),
        IMAGE_NOT_CONFIRMED);
  }

  public GetCompanyDetailResponse getCompanyDetail(String companyId, Caller caller) {
    return DownstreamErrors.call(
        () -> lynqBackendClient
            .getCompanyDetail(companyId, caller.requestUuid(), caller.authorization())
            .getData(),
        COMPANY_UNREADABLE);
  }
}
