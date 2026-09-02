package com.lynq.backend.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.uuid.Generators;
import com.lynq.backend.aspect.AuditLog;
import com.lynq.backend.controller.request.CreateResumeRequest;
import com.lynq.backend.controller.request.UpdateUserProfileRequest;
import com.lynq.backend.controller.response.DeleteResumeRestResponse;
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
import com.lynq.backend.model.UserSimilarityTagEntity;
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
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
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
  private static final String RESUME_NOT_SERIALIZABLE = "The resume could not be serialized to JSON";
  private static final String RESUME_FILE_ALREADY_USED =
      "File '%s' already backs one of the user's resumes";
  private static final String RESUME_NOT_FOUND = "Resume '%s' not found";
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
  @Transactional
  public GetUserResumeRestResponse createResume(String userId, CreateResumeRequest request) {
    UserEntity user = userRepository.findById(userId)
        .orElseThrow(() -> new NotFoundException(String.format(USER_NOT_FOUND, userId)));

    if (user.getType() != UserType.CANDIDATE) {
      throw new BadRequestException(ONLY_CANDIDATE_USERS_CAN_UPLOAD_RESUMES);
    }

    if (holdsResumeFile(userId, request.getFileId())) {
      throw new BadRequestException(String.format(RESUME_FILE_ALREADY_USED, request.getFileId()));
    }

    UserResumeEntity resume = UserResumeEntity.builder()
        .id(Generators.timeBasedEpochGenerator().generate().toString())
        .resume(writeResume(request.getResume()))
        .language(request.getLanguage())
        .createdOn(LocalDate.now(ZoneOffset.UTC))
        .name(request.getName())
        .lynqFileStorageId(request.getFileId())
        .user(user)
        .build();

    userResumeRepository.save(resume);
    syncCandidateSkills(user, request);
    userRepository.save(user);

    return toResponse(resume, fileStorageService.obtainDownloadUrl(request.getFileId()));
  }

  /**
   * Feed the candidate's LyNQ score from the resume they just created.
   *
   * <p>The score matches a job's requirements against what the candidate has, and this is the only
   * place in the product where a candidate's skills are ever written down: they come out of the
   * resume (typed in the wizard, or extracted from an uploaded document by lynq-ml). The technical
   * skills and the tools are taken — a soft skill never matches a job requirement — together with
   * the generalized capability tags, which is what lets a candidate who used RabbitMQ match a post
   * asking for Kafka.
   *
   * <p>New entries are added to what the candidate already has rather than replacing it: the same
   * person may hold several resumes (one per language, or one per role they apply for), and the
   * skills of the one they are not looking at right now are no less true.
   */
  private void syncCandidateSkills(UserEntity user, CreateResumeRequest request) {
    Map<String, Object> skills = resumeSkills(request.getResume());

    addSkills(user, listOf(skills.get("technical")));
    addSkills(user, listOf(skills.get("tools")));
    addSimilarityTags(user, request.getSimilarityTags());
  }

  private void addSkills(UserEntity user, List<String> skills) {
    Set<String> existing = user.getSkills().stream()
        .map(skill -> skill.getSkill().toLowerCase())
        .collect(Collectors.toCollection(LinkedHashSet::new));

    clean(skills).stream()
        .filter(skill -> existing.add(skill.toLowerCase()))
        .map(skill -> UserSkillsEntity.builder()
            .id(Generators.timeBasedEpochGenerator().generate().toString())
            .user(user)
            .skill(skill)
            .build())
        .forEach(user.getSkills()::add);
  }

  private void addSimilarityTags(UserEntity user, List<String> similarityTags) {
    Set<String> existing = user.getSimilarityTags().stream()
        .map(tag -> tag.getSimilarityTag().toLowerCase())
        .collect(Collectors.toCollection(LinkedHashSet::new));

    clean(similarityTags).stream()
        .filter(tag -> existing.add(tag.toLowerCase()))
        .map(tag -> UserSimilarityTagEntity.builder()
            .id(Generators.timeBasedEpochGenerator().generate().toString())
            .user(user)
            .similarityTag(tag)
            .build())
        .forEach(user.getSimilarityTags()::add);
  }

  /**
   * The {@code skills} object of a resume payload. The resume arrives as free-form JSON (the wizard
   * and the parser both produce it), so a draft that carries no skills block is normal and yields an
   * empty map rather than an error.
   */
  @SuppressWarnings("unchecked")
  private Map<String, Object> resumeSkills(Object resume) {
    if (!(resume instanceof Map<?, ?> fields)) {
      return Map.of();
    }
    Object skills = fields.get("skills");
    return skills instanceof Map<?, ?> ? (Map<String, Object>) skills : Map.of();
  }

  /** One bucket of the resume's skills object, ignoring anything that isn't a list of text. */
  private static List<String> listOf(Object value) {
    if (!(value instanceof List<?> items)) {
      return List.of();
    }
    return items.stream()
        .filter(String.class::isInstance)
        .map(String.class::cast)
        .toList();
  }

  /** Trimmed, de-duplicated, blanks dropped — what actually reaches the tables. */
  private static List<String> clean(List<String> values) {
    return values == null ? List.of() : values.stream()
        .filter(Objects::nonNull)
        .map(String::trim)
        .filter(value -> !value.isEmpty())
        .distinct()
        .toList();
  }

  /**
   * Delete one of the authenticated candidate's resumes.
   *
   * <p>Scoped to the caller on purpose: the resume is looked up among the ones that belong to this
   * user, so another candidate's id resolves to "not found" rather than to a forbidden — a resume
   * the caller does not own should not even be acknowledged as existing.
   *
   * <p>The candidate's skills are deliberately left alone. They were merged from every resume the
   * person has written (see {@link #syncCandidateSkills}), so there is no way to tell which of them
   * came from this one, and dropping a skill the candidate still has elsewhere would silently lower
   * their LyNQ score.
   *
   * @return the deleted resume's id together with the file id of its PDF, which lynq-bff uses to
   *     drop the file from lynq-file-storage.
   */
  @AuditLog
  @Transactional
  public DeleteResumeRestResponse deleteResume(String userId, String resumeId) {
    UserEntity user = userRepository.findById(userId)
        .orElseThrow(() -> new NotFoundException(String.format(USER_NOT_FOUND, userId)));

    if (user.getType() != UserType.CANDIDATE) {
      throw new BadRequestException(ONLY_CANDIDATE_USERS_CAN_ACCESS_RESUMES);
    }

    UserResumeEntity resume = userResumeRepository.findByUserId(userId).stream()
        .filter(owned -> owned.getId().equals(resumeId))
        .findFirst()
        .orElseThrow(() -> new NotFoundException(String.format(RESUME_NOT_FOUND, resumeId)));

    userResumeRepository.delete(resume);

    return DeleteResumeRestResponse.builder()
        .id(resume.getId())
        .fileId(resume.getLynqFileStorageId())
        .build();
  }

  private boolean holdsResumeFile(String userId, String fileId) {
    return userResumeRepository.findByUserId(userId).stream()
        .anyMatch(resume -> fileId.equals(resume.getLynqFileStorageId()));
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

    // The candidate is the same for every application, so their skills and capability tags are
    // read once here rather than pulled per-row by the query, and reused to score each job the
    // candidate applied to.
    List<String> candidateSkills = user.getSkills() == null ? List.of() : user.getSkills().stream()
        .map(UserSkillsEntity::getSkill)
        .toList();
    List<String> candidateSimilarityTags = user.getSimilarityTags() == null ? List.of()
        : user.getSimilarityTags().stream()
            .map(UserSimilarityTagEntity::getSimilarityTag)
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
        .map(projection ->
            toApplicationResponse(projection, candidateSkills, candidateSimilarityTags, logoUrls)));
  }

  private UserApplicationResponse toApplicationResponse(UserApplicationProjection projection,
      List<String> candidateSkills, List<String> candidateSimilarityTags, Map<String, String> logoUrls) {
    return UserApplicationResponse.builder()
        .id(projection.id())
        .jobId(projection.jobId())
        .jobTitle(projection.jobTitle())
        .jobDescription(projection.jobDescription())
        .companyId(projection.companyId())
        .companyName(projection.companyName())
        .companyProfileImage(signedUrl(logoUrls, projection.companyFileStorageId()))
        .appliedOn(projection.appliedOn())
        .lynqScore(LyNQScoreCalculator.score(splitSkills(projection.jobSkills()),
            splitSkills(projection.jobSimilarityTags()), candidateSkills, candidateSimilarityTags))
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
    return toResponse(resume, signedUrl(pdfUrls, resume.getLynqFileStorageId()));
  }

  private GetUserResumeRestResponse toResponse(UserResumeEntity resume, String pdfUrl) {
    return GetUserResumeRestResponse.builder()
        .id(resume.getId())
        .name(resume.getName())
        .language(resume.getLanguage())
        .createdOn(resume.getCreatedOn())
        .resume(parseResume(resume.getResume()))
        .pdfUrl(pdfUrl)
        .build();
  }

  private String writeResume(Object resume) {
    try {
      return objectMapper.writeValueAsString(resume);
    } catch (JsonProcessingException e) {
      throw new BadRequestException(RESUME_NOT_SERIALIZABLE);
    }
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