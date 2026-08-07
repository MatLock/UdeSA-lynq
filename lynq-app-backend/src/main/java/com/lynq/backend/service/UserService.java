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
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
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
  private static final String NOT_THE_CURRENT_PROFILE_IMAGE =
      "File '%s' is not the profile image currently registered for the user";

  private final UserRepository userRepository;
  private final UserResumeRepository userResumeRepository;
  private final CompanyRepository companyRepository;
  private final JobPostRepository jobPostRepository;
  private final UserApplicationJobRepository userApplicationJobRepository;
  private final FileStorageService fileStorageService;
  private final ObjectMapper objectMapper;

  public UserService(UserRepository userRepository, UserResumeRepository userResumeRepository,
      CompanyRepository companyRepository, JobPostRepository jobPostRepository,
      UserApplicationJobRepository userApplicationJobRepository,
      FileStorageService fileStorageService, ObjectMapper objectMapper){
    this.userRepository = userRepository;
    this.userResumeRepository = userResumeRepository;
    this.companyRepository = companyRepository;
    this.jobPostRepository = jobPostRepository;
    this.userApplicationJobRepository = userApplicationJobRepository;
    this.fileStorageService = fileStorageService;
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
        .createdOn(LocalDate.now(ZoneOffset.UTC))
        .build();

    return userRepository.save(user);
  }

  @AuditLog
  @Transactional(readOnly = true)
  public UserEntity getUser(String userId) {
    return userRepository.findById(userId)
        .orElseThrow(() -> new NotFoundException(String.format(USER_NOT_FOUND, userId)));
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
        .profileImageUrl(fileStorageService.obtainDownloadUrl(company.getLynqFileStorageId()))
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

  @AuditLog
  @Transactional
  public UserEntity updateUserProfile(String userId, UpdateUserProfileRequest request) {
    UserEntity user = userRepository.findById(userId)
        .orElseThrow(() -> new NotFoundException(String.format(USER_NOT_FOUND, userId)));

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

  /**
   * Registers the new profile image in lynq-file-storage, points the user at it and drops the file
   * it replaces. The returned upload URL is short-lived and the file only becomes readable once the
   * browser has PUT the bytes and called {@link #confirmProfileImageUpload(String, String)}.
   */
  @AuditLog
  @Transactional
  public RegisteredUpload generateProfileImageUploadUrl(String userId, String fileName) {
    UserEntity user = userRepository.findById(userId)
        .orElseThrow(() -> new NotFoundException(String.format(USER_NOT_FOUND, userId)));

    String previousFileId = user.getLynqFileStorageId();
    RegisteredUpload upload = fileStorageService.registerUpload(fileName);

    user.setLynqFileStorageId(upload.fileId());
    userRepository.save(user);

    if (previousFileId != null && !previousFileId.isBlank()
        && !previousFileId.equals(upload.fileId())) {
      fileStorageService.deleteFile(previousFileId);
    }

    return upload;
  }

  @AuditLog
  @Transactional(readOnly = true)
  public void confirmProfileImageUpload(String userId, String fileId) {
    UserEntity user = userRepository.findById(userId)
        .orElseThrow(() -> new NotFoundException(String.format(USER_NOT_FOUND, userId)));

    if (!fileId.equals(user.getLynqFileStorageId())) {
      throw new BadRequestException(String.format(NOT_THE_CURRENT_PROFILE_IMAGE, fileId));
    }

    fileStorageService.confirmUpload(fileId);
  }

  @AuditLog
  @Transactional(readOnly = true)
  public RegisteredUpload generateResumeUploadUrl(String userId, String fileName) {
    UserEntity user = userRepository.findById(userId)
        .orElseThrow(() -> new NotFoundException(String.format(USER_NOT_FOUND, userId)));

    if (user.getType() != UserType.CANDIDATE) {
      throw new BadRequestException(ONLY_CANDIDATE_USERS_CAN_UPLOAD_RESUMES);
    }

    return fileStorageService.registerUpload(fileName);
  }

  /**
   * Resume rows are written by the ingestion pipeline, not here, so the file id cannot be looked up
   * from the database: the caller confirms the id it got from
   * {@link #generateResumeUploadUrl(String, String)}.
   */
  @AuditLog
  @Transactional(readOnly = true)
  public void confirmResumeUpload(String userId, String fileId) {
    UserEntity user = userRepository.findById(userId)
        .orElseThrow(() -> new NotFoundException(String.format(USER_NOT_FOUND, userId)));

    if (user.getType() != UserType.CANDIDATE) {
      throw new BadRequestException(ONLY_CANDIDATE_USERS_CAN_UPLOAD_RESUMES);
    }

    fileStorageService.confirmUpload(fileId);
  }

  @AuditLog
  @Transactional(readOnly = true)
  public String obtainProfileImagePreSignedUrl(UserEntity user) {
    return fileStorageService.obtainDownloadUrl(user.getLynqFileStorageId());
  }

  @AuditLog
  @Transactional(readOnly = true)
  public List<GetUserResumeRestResponse> getUserResumes(String userId) {
    UserEntity user = userRepository.findById(userId)
        .orElseThrow(() -> new NotFoundException(String.format(USER_NOT_FOUND, userId)));

    if (user.getType() != UserType.CANDIDATE) {
      throw new BadRequestException(ONLY_CANDIDATE_USERS_CAN_ACCESS_RESUMES);
    }

    List<UserResumeEntity> resumes = userResumeRepository.findByUserId(userId);

    // As with the applications page, every resume PDF is signed in one call.
    Map<String, String> pdfUrls = fileStorageService.obtainDownloadUrls(resumes.stream()
        .map(UserResumeEntity::getLynqFileStorageId)
        .toList());

    return resumes.stream()
        .map(resume -> toResponse(resume, pdfUrls))
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

    Page<UserApplicationProjection> applications =
        userApplicationJobRepository.findApplicationsByUserId(userId, pageable);

    // Every company logo on the page is signed in a single call to lynq-file-storage rather than
    // one call per row.
    Map<String, String> logoUrls = fileStorageService.obtainDownloadUrls(
        applications.getContent().stream()
            .map(UserApplicationProjection::companyFileStorageId)
            .toList());

    return PagedRestResponse.from(applications
        .map(projection -> toApplicationResponse(projection, candidateSkills, logoUrls)));
  }

  private UserApplicationResponse toApplicationResponse(UserApplicationProjection projection,
      List<String> candidateSkills, Map<String, String> logoUrls) {
    return UserApplicationResponse.builder()
        .id(projection.id())
        .jobId(projection.jobId())
        .jobTitle(projection.jobTitle())
        .jobDescription(projection.jobDescription())
        .companyId(projection.companyId())
        .companyName(projection.companyName())
        .companyProfileImage(signedUrl(logoUrls, projection.companyFileStorageId()))
        .appliedOn(projection.appliedOn())
        .lynqScore(LyNQScoreCalculator.score(splitSkills(projection.jobSkills()), candidateSkills))
        .build();
  }

  /** Rows without a file — a scraped job with no company, a resume with no PDF — get no URL. */
  private static String signedUrl(Map<String, String> downloadUrls, String fileId) {
    return fileId == null ? null : downloadUrls.get(fileId);
  }

  private static List<String> splitSkills(String concatenatedSkills) {
    if (concatenatedSkills == null || concatenatedSkills.isBlank()) {
      return List.of();
    }
    return Arrays.stream(concatenatedSkills.split(","))
        .map(String::trim)
        .toList();
  }

  private GetUserResumeRestResponse toResponse(UserResumeEntity resume,
      Map<String, String> pdfUrls) {
    return GetUserResumeRestResponse.builder()
        .id(resume.getId())
        .name(resume.getName())
        .language(resume.getLanguage())
        .createdOn(resume.getCreatedOn())
        .resume(parseResume(resume.getResume()))
        .pdfUrl(signedUrl(pdfUrls, resume.getLynqFileStorageId()))
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

}