package com.lynq.backend.service;

import com.fasterxml.uuid.Generators;
import com.lynq.backend.aspect.AuditLog;
import com.lynq.backend.controller.request.CreateUserWithCompanyRequest;
import com.lynq.backend.controller.response.CompanyJobRestResponse;
import com.lynq.backend.controller.response.GetCompanyDetailRestResponse;
import com.lynq.backend.enums.UserType;
import com.lynq.backend.exceptions.BadRequestException;
import com.lynq.backend.exceptions.NotFoundException;
import com.lynq.backend.model.CompanyEntity;
import com.lynq.backend.model.JobPostEntity;
import com.lynq.backend.model.UserEntity;
import com.lynq.backend.repository.CompanyRepository;
import com.lynq.backend.repository.JobPostRepository;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CompanyService {

  private static final String COMPANY_NOT_FOUND = "Company '%s' not found";

  private final UserService userService;
  private final CompanyRepository companyRepository;
  private final JobPostRepository jobPostRepository;
  private final StorageService storageService;

  public CompanyService(UserService userService, CompanyRepository companyRepository,
      JobPostRepository jobPostRepository, StorageService storageService) {
    this.userService = userService;
    this.companyRepository = companyRepository;
    this.jobPostRepository = jobPostRepository;
    this.storageService = storageService;
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
        .profileImageUrl(request.getCompanyProfileImageUrl())
        .createdOn(LocalDate.now())
        .owner(owner)
        .build();

    return companyRepository.save(company);
  }

  /**
   * Build the S3 path for the company logo of the company owned by the given user, persist it as the
   * company's profile image reference, and return a short-lived pre-signed upload URL. Mirrors
   * {@link UserService#generateProfileImageUploadUrl}: calling it again replaces the stored
   * reference and best-effort deletes the previous object.
   */
  @AuditLog
  @Transactional
  public String generateCompanyImageUploadUrl(String userId, String fileName) {
    UserEntity owner = userService.getUser(userId);
    CompanyEntity company = companyRepository.findByOwner(owner)
        .orElseThrow(() -> new NotFoundException("No company owned by user '" + userId + "'"));

    String previousImagePath = company.getProfileImageUrl();
    PreSignedUploadUrl preSignedUploadUrl =
        storageService.createCompanyProfilePreSignedUrl(company, fileName);

    company.setProfileImageUrl(preSignedUploadUrl.s3Path());
    companyRepository.save(company);

    if (previousImagePath != null && !previousImagePath.isBlank()
        && !previousImagePath.equals(preSignedUploadUrl.s3Path())) {
      storageService.deleteObject(previousImagePath);
    }

    return preSignedUploadUrl.url();
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
        .profileImageUrl(obtainImageUrl(company.getProfileImageUrl()))
        .createdOn(company.getCreatedOn())
        .jobs(jobPostRepository.findByCompanyId(companyId).stream()
            .map(this::toJobResponse)
            .toList())
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

  private String obtainImageUrl(String s3Path) {
    if (s3Path == null || s3Path.isBlank()) {
      return null;
    }
    return storageService.obtainProfilePreSignedUrl(s3Path);
  }

  private void validateCompanyNameIsUnique(String companyName) {
    if (companyRepository.existsByName(companyName)) {
      throw new BadRequestException("A company with name '" + companyName + "' already exists");
    }
  }

}