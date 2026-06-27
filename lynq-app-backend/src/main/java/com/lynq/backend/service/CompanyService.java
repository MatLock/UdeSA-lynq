package com.lynq.backend.service;

import com.fasterxml.uuid.Generators;
import com.lynq.backend.aspect.AuditLog;
import com.lynq.backend.controller.request.CreateUserWithCompanyRequest;
import com.lynq.backend.enums.UserType;
import com.lynq.backend.exceptions.BadRequestException;
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

  public CompanyService(UserService userService, CompanyRepository companyRepository) {
    this.userService = userService;
    this.companyRepository = companyRepository;
  }

  @AuditLog
  @Transactional
  public CompanyEntity createUserWithCompany(String userId, CreateUserWithCompanyRequest request) {
    validateCompanyNameIsUnique(request.getCompanyName());

    UserEntity owner = userService.saveNewUser(
        userId,
        UserType.COMPANY,
        null,
        request.getUserProfileImageUrl(),
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

  private void validateCompanyNameIsUnique(String companyName) {
    if (companyRepository.existsByName(companyName)) {
      throw new BadRequestException("A company with name '" + companyName + "' already exists");
    }
  }

}