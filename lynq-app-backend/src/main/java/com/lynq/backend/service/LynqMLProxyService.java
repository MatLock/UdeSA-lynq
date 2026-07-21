package com.lynq.backend.service;

import com.lynq.backend.aspect.AuditLog;
import com.lynq.backend.client.LynqMLClient;
import com.lynq.backend.client.request.CandidateSpec;
import com.lynq.backend.client.request.JobSpec;
import com.lynq.backend.client.request.SkillEnhanceRequest;
import com.lynq.backend.client.response.CandidateExplanationResponse;
import com.lynq.backend.client.response.SkillEnhanceResponse;
import com.lynq.backend.client.response.UpskillingSuggestionResponse;
import com.lynq.backend.controller.request.CandidateEvaluationRequest;
import com.lynq.backend.controller.response.GlobalRestResponse;
import com.lynq.backend.enums.UserType;
import com.lynq.backend.enums.WorkType;
import com.lynq.backend.exceptions.BadRequestException;
import com.lynq.backend.model.CompanyEntity;
import com.lynq.backend.model.UserEntity;
import com.lynq.backend.repository.CompanyRepository;
import com.lynq.backend.repository.UserRepository;
import com.lynq.backend.security.LynqUserPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/**
 * Proxies skill-enhance requests to the lynq-ml service. The authenticated
 * user is resolved from the security context and their company from the
 * database so the {@code user-id} and {@code company-id} headers required by
 * lynq-ml can be supplied. The {@code lynq-request-uuid} is forwarded from the
 * incoming request header.
 */
@Service
public class LynqMLProxyService {

  private static final String ONLY_COMPANY_USERS_CAN_ENHANCE_SKILLS =
      "Only users of type COMPANY can enhance skills";
  private static final String ONLY_COMPANY_USERS_CAN_EVALUATE_CANDIDATES =
      "Only users of type COMPANY can evaluate candidates";
  private static final String USER_NOT_LINKED_TO_COMPANY = "User is not linked to any company";
  private static final String AUTHENTICATED_USER_NOT_FOUND = "Authenticated user not found";

  private final LynqMLClient lynqMLClient;
  private final UserRepository userRepository;
  private final CompanyRepository companyRepository;

  public LynqMLProxyService(LynqMLClient lynqMLClient, UserRepository userRepository,
      CompanyRepository companyRepository) {
    this.lynqMLClient = lynqMLClient;
    this.userRepository = userRepository;
    this.companyRepository = companyRepository;
  }

  @AuditLog
  public SkillEnhanceResponse enhanceSkills(String title, String description, WorkType workType,
      String requestUuid) {
    UserEntity user = getAuthenticatedUser();

    if (user.getType() != UserType.COMPANY) {
      throw new BadRequestException(ONLY_COMPANY_USERS_CAN_ENHANCE_SKILLS);
    }

    CompanyEntity company = companyRepository.findByOwner(user)
        .orElseThrow(() -> new BadRequestException(USER_NOT_LINKED_TO_COMPANY));

    SkillEnhanceRequest request = SkillEnhanceRequest.builder()
        .title(title)
        .description(description)
        .workType(workType)
        .build();

    GlobalRestResponse<SkillEnhanceResponse> response = lynqMLClient.enhanceSkills(
        request, requestUuid, user.getId(), company.getId());

    return response.getData();
  }

  @AuditLog
  public UpskillingSuggestionResponse suggestUpskilling(CandidateEvaluationRequest request,
      String requestUuid) {
    CompanyEntity company = getAuthenticatedCompany();

    GlobalRestResponse<UpskillingSuggestionResponse> response = lynqMLClient.upskillingSuggestion(
        toClientRequest(request), requestUuid, company.getOwner().getId(), company.getId());

    return response.getData();
  }

  @AuditLog
  public CandidateExplanationResponse explainCandidate(CandidateEvaluationRequest request,
      String requestUuid) {
    CompanyEntity company = getAuthenticatedCompany();

    GlobalRestResponse<CandidateExplanationResponse> response = lynqMLClient.candidateExplanation(
        toClientRequest(request), requestUuid, company.getOwner().getId(), company.getId());

    return response.getData();
  }

  /**
   * Resolves the authenticated user, enforces that it is a COMPANY-type user
   * linked to a company, and returns that company. Shared by the endpoints that
   * evaluate a candidate against a job.
   */
  private CompanyEntity getAuthenticatedCompany() {
    UserEntity user = getAuthenticatedUser();

    if (user.getType() != UserType.COMPANY) {
      throw new BadRequestException(ONLY_COMPANY_USERS_CAN_EVALUATE_CANDIDATES);
    }

    return companyRepository.findByOwner(user)
        .orElseThrow(() -> new BadRequestException(USER_NOT_LINKED_TO_COMPANY));
  }

  private static com.lynq.backend.client.request.CandidateEvaluationRequest toClientRequest(
      CandidateEvaluationRequest request) {
    return com.lynq.backend.client.request.CandidateEvaluationRequest.builder()
        .job(JobSpec.builder()
            .description(request.getJob().getDescription())
            .skills(request.getJob().getSkills())
            .build())
        .candidate(CandidateSpec.builder()
            .description(request.getCandidate().getDescription())
            .skills(request.getCandidate().getSkills())
            .build())
        .build();
  }

  private UserEntity getAuthenticatedUser() {
    LynqUserPrincipal principal = (LynqUserPrincipal) SecurityContextHolder.getContext()
        .getAuthentication()
        .getPrincipal();

    return userRepository.findById(principal.getId())
        .orElseThrow(() -> new BadRequestException(AUTHENTICATED_USER_NOT_FOUND));
  }
}