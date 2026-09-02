package com.lynq.backend.service;

import com.fasterxml.uuid.Generators;
import com.lynq.backend.aspect.AuditLog;
import com.lynq.backend.controller.request.CreateUserWithCompanyRequest;
import com.lynq.backend.controller.request.UpdateCompanyRequest;
import com.lynq.backend.controller.response.CompanyJobRestResponse;
import com.lynq.backend.controller.response.GetCompanyDetailRestResponse;
import com.lynq.backend.controller.response.UpdateCompanyRestResponse;
import com.lynq.backend.enums.UserType;
import com.lynq.backend.exceptions.BadRequestException;
import com.lynq.backend.exceptions.NotFoundException;
import com.lynq.backend.model.CompanyEntity;
import com.lynq.backend.model.JobPostEntity;
import com.lynq.backend.model.UserEntity;
import com.lynq.backend.repository.CompanyRepository;
import com.lynq.backend.repository.JobPostRepository;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CompanyService {

  private static final String COMPANY_NOT_FOUND = "Company '%s' not found";
  private static final String NO_COMPANY_OWNED_BY_USER = "No company owned by user '%s'";
  private static final String NOT_THE_CURRENT_COMPANY_LOGO =
      "File '%s' is not the logo currently registered for the company";

  private final UserService userService;
  private final CompanyRepository companyRepository;
  private final JobPostRepository jobPostRepository;
  private final FileStorageService fileStorageService;

  public CompanyService(UserService userService, CompanyRepository companyRepository,
      JobPostRepository jobPostRepository, FileStorageService fileStorageService) {
    this.userService = userService;
    this.companyRepository = companyRepository;
    this.jobPostRepository = jobPostRepository;
    this.fileStorageService = fileStorageService;
  }

  @AuditLog
  @Transactional
  public CompanyEntity createUserWithCompany(String userId, CreateUserWithCompanyRequest request) {
    validateCompanyNameIsUnique(request.getCompanyName());

    UserEntity owner = userService.saveNewUser(
        userId,
        UserType.COMPANY,
        request.getFullName(),
        request.getCurrentPosition(),
        request.getUserAbout(),
        null,
        request.getLinkedinUrl(),
        request.getBirthDate());

    CompanyEntity company = CompanyEntity.builder()
        .id(Generators.timeBasedEpochGenerator().generate().toString())
        .name(request.getCompanyName())
        .about(request.getCompanyAbout())
        .size(request.getCompanySize())
        .createdOn(LocalDate.now(ZoneOffset.UTC))
        .owner(owner)
        .build();

    return companyRepository.save(company);
  }

  @AuditLog
  @Transactional
  public RegisteredUpload generateCompanyImageUploadUrl(String userId, String fileName) {
    CompanyEntity company = getOwnedCompany(userId);

    String previousFileId = company.getLynqFileStorageId();
    RegisteredUpload upload = fileStorageService.registerUpload(fileName);

    company.setLynqFileStorageId(upload.fileId());
    companyRepository.save(company);

    if (previousFileId != null && !previousFileId.isBlank()
        && !previousFileId.equals(upload.fileId())) {
      fileStorageService.deleteFile(previousFileId);
    }

    return upload;
  }

  @AuditLog
  @Transactional(readOnly = true)
  public void confirmCompanyImageUpload(String userId, String fileId) {
    CompanyEntity company = getOwnedCompany(userId);

    if (!fileId.equals(company.getLynqFileStorageId())) {
      throw new BadRequestException(String.format(NOT_THE_CURRENT_COMPANY_LOGO, fileId));
    }

    fileStorageService.confirmUpload(fileId);
  }

  @AuditLog
  @Transactional(readOnly = true)
  public GetCompanyDetailRestResponse getCompanyDetail(String companyId) {
    CompanyEntity company = companyRepository.findById(companyId)
        .orElseThrow(() -> new NotFoundException(String.format(COMPANY_NOT_FOUND, companyId)));

    return GetCompanyDetailRestResponse.builder()
        .id(company.getId())
        .name(company.getName())
        .about(company.getAbout())
        .size(company.getSize())
        .profileImageUrl(fileStorageService.obtainDownloadUrl(company.getLynqFileStorageId()))
        .createdOn(company.getCreatedOn())
        .jobs(jobPostRepository.findByCompanyId(companyId).stream()
            .map(this::toJobResponse)
            .toList())
        .build();
  }

  @AuditLog
  @Transactional
  public UpdateCompanyRestResponse updateCompany(String userId, UpdateCompanyRequest request) {
    CompanyEntity company = getOwnedCompany(userId);

    if (request.getName() != null && !request.getName().equals(company.getName())) {
      validateCompanyNameIsUnique(request.getName());
      company.setName(request.getName());
    }
    if (request.getAbout() != null) {
      company.setAbout(request.getAbout());
    }
    if (request.getSize() != null) {
      company.setSize(request.getSize());
    }

    CompanyEntity saved = companyRepository.save(company);

    return UpdateCompanyRestResponse.builder()
        .id(saved.getId())
        .name(saved.getName())
        .about(saved.getAbout())
        .size(saved.getSize())
        .profileImageUrl(fileStorageService.obtainDownloadUrl(saved.getLynqFileStorageId()))
        .createdOn(saved.getCreatedOn())
        .build();
  }

  private CompanyJobRestResponse toJobResponse(JobPostEntity job) {
    return CompanyJobRestResponse.builder()
        .id(job.getId())
        .title(job.getTitle())
        .description(job.getDescription())
        .jobStatus(job.getJobStatus())
        .build();
  }

  private CompanyEntity getOwnedCompany(String userId) {
    UserEntity owner = userService.getUser(userId);
    return companyRepository.findByOwner(owner)
        .orElseThrow(() -> new NotFoundException(String.format(NO_COMPANY_OWNED_BY_USER, userId)));
  }

  private void validateCompanyNameIsUnique(String companyName) {
    if (companyRepository.existsByName(companyName)) {
      throw new BadRequestException("A company with name '" + companyName + "' already exists");
    }
  }

}