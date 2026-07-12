package com.lynq.backend.service;

import com.fasterxml.uuid.Generators;
import com.lynq.backend.aspect.AuditLog;
import com.lynq.backend.controller.request.CreateUserWithCompanyRequest;
import com.lynq.backend.enums.UserType;
import com.lynq.backend.exceptions.BadRequestException;
import com.lynq.backend.exceptions.NotFoundException;
import com.lynq.backend.model.CompanyEntity;
import com.lynq.backend.model.UserEntity;
import com.lynq.backend.repository.CompanyRepository;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CompanyService {

  private final UserService userService;
  private final CompanyRepository companyRepository;
  private final StorageService storageService;

  public CompanyService(UserService userService, CompanyRepository companyRepository,
      StorageService storageService) {
    this.userService = userService;
    this.companyRepository = companyRepository;
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

  private void validateCompanyNameIsUnique(String companyName) {
    if (companyRepository.existsByName(companyName)) {
      throw new BadRequestException("A company with name '" + companyName + "' already exists");
    }
  }

}