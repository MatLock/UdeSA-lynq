package com.lynq.backend.service;

import com.fasterxml.uuid.Generators;
import com.lynq.backend.aspect.AuditLog;
import com.lynq.backend.enums.JobPostSource;
import com.lynq.backend.enums.UserType;
import com.lynq.backend.enums.WorkType;
import com.lynq.backend.exceptions.BadRequestException;
import com.lynq.backend.model.CompanyEntity;
import com.lynq.backend.model.JobPostEntity;
import com.lynq.backend.model.JobPostSkillEntity;
import com.lynq.backend.model.UserEntity;
import com.lynq.backend.repository.CompanyRepository;
import com.lynq.backend.repository.JobPostRepository;
import com.lynq.backend.repository.UserRepository;
import com.lynq.backend.security.LynqUserPrincipal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JobService {

  private static final String ONLY_COMPANY_USERS_CAN_CREATE_JOBS = "Only users of type COMPANY can create jobs";
  private static final String USER_NOT_LINKED_TO_COMPANY = "User is not linked to any company";
  private static final String AUTHENTICATED_USER_NOT_FOUND = "Authenticated user not found";

  private final JobPostRepository jobPostRepository;
  private final CompanyRepository companyRepository;
  private final UserRepository userRepository;

  public JobService(JobPostRepository jobPostRepository, CompanyRepository companyRepository,
      UserRepository userRepository) {
    this.jobPostRepository = jobPostRepository;
    this.companyRepository = companyRepository;
    this.userRepository = userRepository;
  }

  @AuditLog
  @Transactional
  public JobPostEntity createJob(String title, String description, WorkType workType,
      Integer salaryRangeDown, Integer salaryRangeTop, JobPostSource jobPostSource,
      List<String> skills) {
    UserEntity user = getAuthenticatedUser();

    if (user.getType() != UserType.COMPANY) {
      throw new BadRequestException(ONLY_COMPANY_USERS_CAN_CREATE_JOBS);
    }

    CompanyEntity company = companyRepository.findByOwner(user)
        .orElseThrow(() -> new BadRequestException(USER_NOT_LINKED_TO_COMPANY));

    JobPostEntity job = JobPostEntity.builder()
        .id(Generators.timeBasedEpochGenerator().generate().toString())
        .title(title)
        .description(description)
        .workType(workType)
        .salaryRangeDown(salaryRangeDown)
        .salaryRangeTop(salaryRangeTop)
        .jobPostSource(jobPostSource)
        .createdOn(LocalDate.now())
        .createdByUser(user)
        .company(company)
        .build();

    addSkills(job, skills);

    return jobPostRepository.save(job);
  }

  private void addSkills(JobPostEntity job, List<String> skills) {
    if (skills == null) {
      return;
    }

    skills.stream()
        .distinct()
        .map(skill -> JobPostSkillEntity.builder()
            .id(Generators.timeBasedEpochGenerator().generate().toString())
            .jobPost(job)
            .skill(skill)
            .build())
        .forEach(job.getSkills()::add);
  }

  private UserEntity getAuthenticatedUser() {
    LynqUserPrincipal principal = (LynqUserPrincipal) SecurityContextHolder.getContext()
        .getAuthentication()
        .getPrincipal();

    return userRepository.findById(principal.getId())
        .orElseThrow(() -> new BadRequestException(AUTHENTICATED_USER_NOT_FOUND));
  }

}