package com.lynq.backend.service;

import com.fasterxml.uuid.Generators;
import com.lynq.backend.aspect.AuditLog;
import com.lynq.backend.controller.response.GetJobRestResponse;
import com.lynq.backend.controller.response.JobCandidateResponse;
import com.lynq.backend.controller.response.JobCompanyRestResponse;
import com.lynq.backend.controller.response.JobPostedByRestResponse;
import com.lynq.backend.controller.response.PagedRestResponse;
import com.lynq.backend.enums.JobPostSource;
import com.lynq.backend.enums.UserType;
import com.lynq.backend.enums.WorkType;
import com.lynq.backend.exceptions.AlreadyAppliedToJobException;
import com.lynq.backend.exceptions.BadRequestException;
import com.lynq.backend.exceptions.NotFoundException;
import com.lynq.backend.model.CompanyEntity;
import com.lynq.backend.model.JobPostEntity;
import com.lynq.backend.model.JobPostSkillEntity;
import com.lynq.backend.model.UserApplicationJobEntity;
import com.lynq.backend.model.UserEntity;
import com.lynq.backend.model.UserSkillsEntity;
import com.lynq.backend.repository.CompanyRepository;
import com.lynq.backend.repository.JobPostRepository;
import com.lynq.backend.repository.UserApplicationJobRepository;
import com.lynq.backend.repository.UserRepository;
import com.lynq.backend.repository.projection.JobCandidateProjection;
import com.lynq.backend.repository.projection.JobWithDetailsProjection;
import com.lynq.backend.security.LynqUserPrincipal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JobService {

  private static final String ONLY_COMPANY_USERS_CAN_CREATE_JOBS = "Only users of type COMPANY can create jobs";
  private static final String ONLY_CANDIDATE_USERS_CAN_APPLY = "Only users of type CANDIDATE can apply to jobs";
  private static final String USER_NOT_LINKED_TO_COMPANY = "User is not linked to any company";
  private static final String AUTHENTICATED_USER_NOT_FOUND = "Authenticated user not found";
  private static final String JOB_POST_NOT_FOUND = "Job post not found";
  private static final String ALREADY_APPLIED_TO_JOB = "User has already applied to this job";

  private final JobPostRepository jobPostRepository;
  private final CompanyRepository companyRepository;
  private final UserRepository userRepository;
  private final UserApplicationJobRepository userApplicationJobRepository;
  private final StorageService storageService;

  public JobService(JobPostRepository jobPostRepository, CompanyRepository companyRepository,
      UserRepository userRepository, UserApplicationJobRepository userApplicationJobRepository,
      StorageService storageService) {
    this.jobPostRepository = jobPostRepository;
    this.companyRepository = companyRepository;
    this.userRepository = userRepository;
    this.userApplicationJobRepository = userApplicationJobRepository;
    this.storageService = storageService;
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

  @AuditLog
  @Transactional
  public JobPostEntity increaseSeen(String jobId) {
    if (jobPostRepository.increaseTotalSeen(jobId) == 0) {
      throw new NotFoundException(JOB_POST_NOT_FOUND);
    }
    return jobPostRepository.findById(jobId)
        .orElseThrow(() -> new NotFoundException(JOB_POST_NOT_FOUND));
  }

  @AuditLog
  @Transactional
  public UserApplicationJobEntity applyToJob(String jobId) {
    UserEntity user = getAuthenticatedUser();

    if (user.getType() != UserType.CANDIDATE) {
      throw new BadRequestException(ONLY_CANDIDATE_USERS_CAN_APPLY);
    }

    JobPostEntity job = jobPostRepository.findById(jobId)
        .orElseThrow(() -> new NotFoundException(JOB_POST_NOT_FOUND));

    if (userApplicationJobRepository.existsByJobIdAndUserId(jobId, user.getId())) {
      throw new AlreadyAppliedToJobException(ALREADY_APPLIED_TO_JOB);
    }

    UserApplicationJobEntity application = UserApplicationJobEntity.builder()
        .id(Generators.timeBasedEpochGenerator().generate().toString())
        .jobPost(job)
        .user(user)
        .appliedOn(LocalDate.now())
        .build();

    return userApplicationJobRepository.save(application);
  }

  @AuditLog
  @Transactional(readOnly = true)
  public PagedRestResponse<JobCandidateResponse> getJobCandidates(String jobId, Pageable pageable) {
    return PagedRestResponse.from(userApplicationJobRepository
        .findCandidatesByJobId(jobId, pageable)
        .map(this::toCandidateResponse));
  }

  private JobCandidateResponse toCandidateResponse(JobCandidateProjection projection) {
    List<String> jobSkills = splitSkills(projection.jobSkills());
    List<String> candidateSkills = splitSkills(projection.userSkills());
    return JobCandidateResponse.builder()
        .id(projection.id())
        .userId(projection.userId())
        .jobId(projection.jobId())
        .userFullName(projection.userFullName())
        .userProfileImage(obtainProfileImageUrl(projection.userProfileImageUrl()))
        .userCurrentPosition(projection.userCurrentPosition())
        .userAppliedOn(projection.appliedOn())
        .lynqScore(calculateLyNQScore(jobSkills, candidateSkills))
        .build();
  }

  @AuditLog
  @Transactional(readOnly = true)
  public PagedRestResponse<GetJobRestResponse> searchAvailableJobs(JobFilter filter,
      Pageable pageable) {
    UserEntity user = getAuthenticatedUser();
    return PagedRestResponse.from(jobPostRepository.searchAvailableJobs(
            filter.filterValue(),
            pageable)
        .map(projection -> toResponse(projection, user)));
  }

  private GetJobRestResponse toResponse(JobWithDetailsProjection projection, UserEntity user) {
    List<String> skills = splitSkills(projection.skills());
    return GetJobRestResponse.builder()
        .jobId(projection.jobId())
        .title(projection.title())
        .description(projection.description())
        .workType(projection.workType())
        .salaryRangeDown(projection.salaryRangeDown())
        .salaryRangeTop(projection.salaryRangeTop())
        .jobUrl(projection.jobUrl())
        .jobPostSource(projection.jobPostSource())
        .createdOn(projection.createdOn())
        .totalSeen(projection.totalSeen())
        .company(JobCompanyRestResponse.builder()
            .id(projection.companyId())
            .name(projection.companyName())
            .about(projection.companyAbout())
            .size(projection.companySize())
            .profileImageUrl(obtainProfileImageUrl(projection.companyProfileImageUrl()))
            .build())
        .postedBy(JobPostedByRestResponse.builder()
            .id(projection.userId())
            .fullName(projection.userFullName())
            .profileImageUrl(obtainProfileImageUrl(projection.userProfileImageUrl()))
            .currentPosition(projection.userCurrentPosition())
            .build())
        .skills(skills)
        .lynqScore(calculateLyNQScore(skills, user))
        .build();
  }

  private static List<String> splitSkills(String concatenatedSkills) {
    if (concatenatedSkills == null || concatenatedSkills.isBlank()) {
      return List.of();
    }
    return Arrays.stream(concatenatedSkills.split(","))
        .map(String::trim)
        .toList();
  }

  private String obtainProfileImageUrl(String s3Path) {
    if (s3Path == null || s3Path.isBlank()) {
      return null;
    }
    return storageService.obtainProfilePreSignedUrl(s3Path);
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

  private Integer calculateLyNQScore(List<String> jobSkillNames, UserEntity user) {
    if (user == null || user.getType() != UserType.CANDIDATE) {
      return null;
    }

    List<UserSkillsEntity> userSkills = user.getSkills();
    List<String> userSkillNames = userSkills == null ? null : userSkills.stream()
        .map(UserSkillsEntity::getSkill)
        .toList();

    return calculateLyNQScore(jobSkillNames, userSkillNames);
  }

  private Integer calculateLyNQScore(List<String> jobSkillNames, List<String> userSkillNames) {
    if (jobSkillNames == null || jobSkillNames.isEmpty() || userSkillNames == null
        || userSkillNames.isEmpty()) {
      return null;
    }

    Set<String> normalizedUserSkills = userSkillNames.stream()
        .filter(Objects::nonNull)
        .map(skill -> skill.trim().toLowerCase())
        .filter(skill -> !skill.isEmpty())
        .collect(Collectors.toSet());

    Set<String> normalizedJobSkills = jobSkillNames.stream()
        .filter(Objects::nonNull)
        .map(skill -> skill.trim().toLowerCase())
        .filter(skill -> !skill.isEmpty())
        .collect(Collectors.toSet());

    if (normalizedJobSkills.isEmpty() || normalizedUserSkills.isEmpty()) {
      return null;
    }

    long matches = normalizedJobSkills.stream()
        .filter(normalizedUserSkills::contains)
        .count();

    return (int) Math.round((matches * 100.0) / normalizedJobSkills.size());
  }

  private UserEntity getAuthenticatedUser() {
    LynqUserPrincipal principal = (LynqUserPrincipal) SecurityContextHolder.getContext()
        .getAuthentication()
        .getPrincipal();

    return userRepository.findById(principal.getId())
        .orElseThrow(() -> new BadRequestException(AUTHENTICATED_USER_NOT_FOUND));
  }

}