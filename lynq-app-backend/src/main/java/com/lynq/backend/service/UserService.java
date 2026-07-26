package com.lynq.backend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lynq.backend.aspect.AuditLog;
import com.lynq.backend.controller.request.UpdateUserProfileRequest;
import com.lynq.backend.controller.response.GetUserProfileRestResponse;
import com.lynq.backend.controller.response.GetUserResumeRestResponse;
import com.lynq.backend.controller.response.PagedRestResponse;
import com.lynq.backend.controller.response.UserApplicationResponse;
import com.lynq.backend.controller.response.UserProfileCompanyRestResponse;
import com.lynq.backend.controller.response.UserProfileJobRestResponse;
import com.lynq.backend.exceptions.BadRequestException;
import com.lynq.backend.exceptions.NotFoundException;
import com.lynq.backend.model.CompanyEntity;
import com.lynq.backend.model.JobPostEntity;
import com.lynq.backend.model.UserEntity;
import com.lynq.backend.model.UserResumeEntity;
import com.lynq.backend.model.UserSkillsEntity;
import com.lynq.backend.enums.UserType;
import com.lynq.backend.repository.CompanyRepository;
import com.lynq.backend.repository.JobPostRepository;
import com.lynq.backend.repository.UserApplicationJobRepository;
import com.lynq.backend.repository.UserRepository;
import com.lynq.backend.repository.UserResumeRepository;
import com.lynq.backend.repository.projection.UserApplicationProjection;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

  private static final String USER_NOT_FOUND = "User '%s' not found";
  private static final String ONLY_CANDIDATE_USERS_CAN_ACCESS_RESUMES =
      "Only users of type CANDIDATE can access resumes";
  private static final String ONLY_CANDIDATE_USERS_CAN_VIEW_APPLICATIONS =
      "Only users of type CANDIDATE can view their applications";
  private static final String ONLY_CANDIDATE_USERS_CAN_UPLOAD_RESUMES =
      "Only users of type CANDIDATE can upload resumes";
  private static final String RESUME_NOT_VALID_JSON = "Stored resume is not valid JSON";

  private final UserRepository userRepository;
  private final UserResumeRepository userResumeRepository;
  private final CompanyRepository companyRepository;
  private final JobPostRepository jobPostRepository;
  private final UserApplicationJobRepository userApplicationJobRepository;
  private final StorageService storageService;
  private final ObjectMapper objectMapper;

  public UserService(UserRepository userRepository, UserResumeRepository userResumeRepository,
      CompanyRepository companyRepository, JobPostRepository jobPostRepository,
      UserApplicationJobRepository userApplicationJobRepository,
      StorageService storageService, ObjectMapper objectMapper){
    this.userRepository = userRepository;
    this.userResumeRepository = userResumeRepository;
    this.companyRepository = companyRepository;
    this.jobPostRepository = jobPostRepository;
    this.userApplicationJobRepository = userApplicationJobRepository;
    this.storageService = storageService;
    this.objectMapper = objectMapper;
  }

  @AuditLog
  @Transactional
  public UserEntity saveNewUser(String userId, UserType type, String fullName,
      String currentPosition, String about, String githubUrl, String linkedInUrl, LocalDate birthDate) {
    UserEntity user = UserEntity.builder()
        .id(userId)
        .type(type)
        .fullName(fullName)
        .currentPosition(currentPosition)
        .about(about)
        .githubUrl(githubUrl)
        .linkedinUrl(linkedInUrl)
        .birthDate(birthDate)
        .createdOn(LocalDate.now())
        .build();

    return userRepository.save(user);
  }

  @AuditLog
  @Transactional(readOnly = true)
  public UserEntity getUser(String userId) {
    return userRepository.findById(userId)
        .orElseThrow(() -> new NotFoundException("User '" + userId + "' not found"));
  }

  @AuditLog
  @Transactional(readOnly = true)
  public String obtainOwnedCompanyId(UserEntity user) {
    if (user.getType() != UserType.COMPANY) {
      return null;
    }
    return companyRepository.findByOwner(user)
        .map(CompanyEntity::getId)
        .orElse(null);
  }

  @AuditLog
  @Transactional(readOnly = true)
  public GetUserProfileRestResponse getUserProfile(String userId) {
    UserEntity user = userRepository.findById(userId)
        .orElseThrow(() -> new NotFoundException(String.format(USER_NOT_FOUND, userId)));

    GetUserProfileRestResponse.GetUserProfileRestResponseBuilder response =
        GetUserProfileRestResponse.builder()
            .fullName(user.getFullName())
            .profileImageUrl(obtainProfileImagePreSignedUrl(user))
            .currentPosition(user.getCurrentPosition())
            .about(user.getAbout())
            .githubUrl(user.getGithubUrl())
            .linkedinUrl(user.getLinkedinUrl());

    if (user.getType() == UserType.COMPANY) {
      companyRepository.findByOwner(user)
          .ifPresent(company -> response.company(toCompanyResponse(company)));
      response.jobs(jobPostRepository.findByCreatedByUserId(userId).stream()
          .map(this::toJobResponse)
          .toList());
    }

    return response.build();
  }

  private UserProfileCompanyRestResponse toCompanyResponse(CompanyEntity company) {
    return UserProfileCompanyRestResponse.builder()
        .name(company.getName())
        .profileImageUrl(obtainImageUrl(company.getProfileImageUrl()))
        .build();
  }

  private UserProfileJobRestResponse toJobResponse(JobPostEntity job) {
    return UserProfileJobRestResponse.builder()
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

  @AuditLog
  @Transactional
  public UserEntity updateUserProfile(String userId, UpdateUserProfileRequest request) {
    UserEntity user = userRepository.findById(userId)
        .orElseThrow(() -> new NotFoundException("User '" + userId + "' not found"));

    if (request.getFullName() != null) {
      user.setFullName(request.getFullName());
    }
    if (request.getCurrentPosition() != null) {
      user.setCurrentPosition(request.getCurrentPosition());
    }
    if (request.getAbout() != null) {
      user.setAbout(request.getAbout());
    }
    if (request.getGithubUrl() != null) {
      user.setGithubUrl(request.getGithubUrl());
    }
    if (request.getLinkedinUrl() != null) {
      user.setLinkedinUrl(request.getLinkedinUrl());
    }
    if (request.getBirthDate() != null) {
      user.setBirthDate(request.getBirthDate());
    }

    return userRepository.save(user);
  }

  @AuditLog
  @Transactional
  public String generateProfileImageUploadUrl(String userId, String fileName) {
    UserEntity user = userRepository.findById(userId)
        .orElseThrow(() -> new NotFoundException("User '" + userId + "' not found"));

    String previousImagePath = user.getProfileImageUrl();
    PreSignedUploadUrl preSignedUploadUrl = storageService.createUserProfilePreSignedUrl(user, fileName);

    user.setProfileImageUrl(preSignedUploadUrl.s3Path());
    userRepository.save(user);

    if (previousImagePath != null && !previousImagePath.isBlank()
        && !previousImagePath.equals(preSignedUploadUrl.s3Path())) {
      storageService.deleteObject(previousImagePath);
    }

    return preSignedUploadUrl.url();
  }

  @AuditLog
  @Transactional(readOnly = true)
  public String generateResumeUploadUrl(String userId, String fileName) {
    UserEntity user = userRepository.findById(userId)
        .orElseThrow(() -> new NotFoundException(String.format(USER_NOT_FOUND, userId)));

    if (user.getType() != UserType.CANDIDATE) {
      throw new BadRequestException(ONLY_CANDIDATE_USERS_CAN_UPLOAD_RESUMES);
    }

    return storageService.createUserResumePreSignedUrl(user, fileName).url();
  }

  @AuditLog
  @Transactional(readOnly = true)
  public String obtainProfileImagePreSignedUrl(UserEntity user) {
    if (user.getProfileImageUrl() == null || user.getProfileImageUrl().isBlank()) {
      return null;
    }
    return storageService.obtainUserProfilePreSignedUrl(user);
  }

  @AuditLog
  @Transactional(readOnly = true)
  public List<GetUserResumeRestResponse> getUserResumes(String userId) {
    UserEntity user = userRepository.findById(userId)
        .orElseThrow(() -> new NotFoundException(String.format(USER_NOT_FOUND, userId)));

    if (user.getType() != UserType.CANDIDATE) {
      throw new BadRequestException(ONLY_CANDIDATE_USERS_CAN_ACCESS_RESUMES);
    }

    return userResumeRepository.findByUserId(userId).stream()
        .map(this::toResponse)
        .toList();
  }

  @AuditLog
  @Transactional(readOnly = true)
  public PagedRestResponse<UserApplicationResponse> getUserApplications(String userId,
      Pageable pageable) {
    UserEntity user = userRepository.findById(userId)
        .orElseThrow(() -> new NotFoundException(String.format(USER_NOT_FOUND, userId)));

    if (user.getType() != UserType.CANDIDATE) {
      throw new BadRequestException(ONLY_CANDIDATE_USERS_CAN_VIEW_APPLICATIONS);
    }

    // The candidate is the same for every application, so their skills are read once here rather
    // than pulled per-row by the query, and reused to score each job the candidate applied to.
    List<String> candidateSkills = user.getSkills() == null ? List.of() : user.getSkills().stream()
        .map(UserSkillsEntity::getSkill)
        .toList();

    return PagedRestResponse.from(userApplicationJobRepository
        .findApplicationsByUserId(userId, pageable)
        .map(projection -> toApplicationResponse(projection, candidateSkills)));
  }

  private UserApplicationResponse toApplicationResponse(UserApplicationProjection projection,
      List<String> candidateSkills) {
    return UserApplicationResponse.builder()
        .id(projection.id())
        .jobId(projection.jobId())
        .jobTitle(projection.jobTitle())
        .jobDescription(projection.jobDescription())
        .companyId(projection.companyId())
        .companyName(projection.companyName())
        .companyProfileImage(obtainImageUrl(projection.companyProfileImageUrl()))
        .appliedOn(projection.appliedOn())
        .lynqScore(LyNQScoreCalculator.score(splitSkills(projection.jobSkills()), candidateSkills))
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

  private GetUserResumeRestResponse toResponse(UserResumeEntity resume) {
    return GetUserResumeRestResponse.builder()
        .id(resume.getId())
        .name(resume.getName())
        .language(resume.getLanguage())
        .createdOn(resume.getCreatedOn())
        .resume(parseResume(resume.getResume()))
        .pdfUrl(obtainPdfUrl(resume.getStoragePath()))
        .build();
  }

  private Object parseResume(String resume) {
    if (resume == null || resume.isBlank()) {
      return null;
    }
    try {
      return objectMapper.readValue(resume, Object.class);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException(RESUME_NOT_VALID_JSON, e);
    }
  }

  private String obtainPdfUrl(String storagePath) {
    if (storagePath == null || storagePath.isBlank()) {
      return null;
    }
    return storageService.obtainProfilePreSignedUrl(storagePath);
  }

}