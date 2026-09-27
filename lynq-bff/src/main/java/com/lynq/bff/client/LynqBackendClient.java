package com.lynq.bff.client;

import com.lynq.bff.client.request.ApplyJobRequest;
import com.lynq.bff.client.request.CreateJobRequest;
import com.lynq.bff.client.request.CreateResumeRequest;
import com.lynq.bff.client.request.CreateUserRequest;
import com.lynq.bff.client.request.CreateUserWithCompanyRequest;
import com.lynq.bff.client.request.UpdateCompanyRequest;
import com.lynq.bff.client.request.UpdateJobRequest;
import com.lynq.bff.client.request.UpdateResumeAliasRequest;
import com.lynq.bff.client.request.UpdateUserProfileRequest;
import com.lynq.bff.client.response.ApplyJobResponse;
import com.lynq.bff.client.response.CandidateExplanationResponse;
import com.lynq.bff.client.response.CloseJobResponse;
import com.lynq.bff.client.response.CreateJobResponse;
import com.lynq.bff.client.response.CreateUserResponse;
import com.lynq.bff.client.response.CreateUserWithCompanyResponse;
import com.lynq.bff.client.response.DeletedResumeResponse;
import com.lynq.bff.client.response.GenerateUploadImageResponse;
import com.lynq.bff.client.response.GenerateUploadResumeResponse;
import com.lynq.bff.client.response.GetCompanyDetailResponse;
import com.lynq.bff.client.response.GetJobResponse;
import com.lynq.bff.client.response.GetUserProfileResponse;
import com.lynq.bff.client.response.GetUserResponse;
import com.lynq.bff.client.response.JobCandidateResponse;
import com.lynq.bff.client.response.JobDetailsResponse;
import com.lynq.bff.client.response.PagedResponse;
import com.lynq.bff.client.response.RefreshJobResponse;
import com.lynq.bff.client.response.SupportedLanguageResponse;
import com.lynq.bff.client.response.UpdateCompanyResponse;
import com.lynq.bff.client.response.UpdateJobResponse;
import com.lynq.bff.client.response.UpdateUserProfileResponse;
import com.lynq.bff.client.response.UpskillingSuggestionResponse;
import com.lynq.bff.client.response.UserApplicationResponse;
import com.lynq.bff.client.response.UserResumeResponse;
import com.lynq.bff.controller.response.GlobalRestResponse;
import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "lynqBackend", url = "${lynq.backend.url}")
public interface LynqBackendClient {

  String REQUEST_UUID_HEADER = "lynq-request-uuid";
  String AUTHORIZATION_HEADER = "Authorization";

  @GetMapping("/dmz/user")
  GlobalRestResponse<GetUserResponse> getUser(
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid,
      @RequestHeader(AUTHORIZATION_HEADER) String authorization);

  @PostMapping("/dmz/user")
  GlobalRestResponse<CreateUserResponse> createUser(
      @RequestBody CreateUserRequest request,
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid,
      @RequestHeader(AUTHORIZATION_HEADER) String authorization);

  @PatchMapping("/dmz/user")
  GlobalRestResponse<UpdateUserProfileResponse> updateUserProfile(
      @RequestBody UpdateUserProfileRequest request,
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid,
      @RequestHeader(AUTHORIZATION_HEADER) String authorization);

  @GetMapping("/dmz/user/generate-upload-image")
  GlobalRestResponse<GenerateUploadImageResponse> generateUserImageUploadUrl(
      @RequestParam("file-name") String fileName,
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid,
      @RequestHeader(AUTHORIZATION_HEADER) String authorization);

  @PostMapping("/dmz/user/confirm-upload-image")
  void confirmUserImageUpload(
      @RequestParam("file-id") String fileId,
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid,
      @RequestHeader(AUTHORIZATION_HEADER) String authorization);

  @GetMapping("/dmz/user/generate-upload-resume")
  GlobalRestResponse<GenerateUploadResumeResponse> generateResumeUploadUrl(
      @RequestParam("file-name") String fileName,
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid,
      @RequestHeader(AUTHORIZATION_HEADER) String authorization);

  @PostMapping("/dmz/user/confirm-upload-resume")
  void confirmResumeUpload(
      @RequestParam("file-id") String fileId,
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid,
      @RequestHeader(AUTHORIZATION_HEADER) String authorization);

  @GetMapping("/dmz/user/resume")
  GlobalRestResponse<List<UserResumeResponse>> getUserResumes(
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid,
      @RequestHeader(AUTHORIZATION_HEADER) String authorization);

  @GetMapping("/dmz/user/resume/languages")
  GlobalRestResponse<List<SupportedLanguageResponse>> getSupportedResumeLanguages(
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid,
      @RequestHeader(AUTHORIZATION_HEADER) String authorization);

  @PostMapping("/dmz/user/resume")
  GlobalRestResponse<UserResumeResponse> createResume(
      @RequestBody CreateResumeRequest request,
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid,
      @RequestHeader(AUTHORIZATION_HEADER) String authorization);

  @PutMapping("/dmz/user/resume/{resumeId}/alias")
  GlobalRestResponse<UserResumeResponse> updateResumeAlias(
      @PathVariable("resumeId") String resumeId,
      @RequestBody UpdateResumeAliasRequest request,
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid,
      @RequestHeader(AUTHORIZATION_HEADER) String authorization);

  @DeleteMapping("/dmz/user/resume/{resumeId}")
  GlobalRestResponse<DeletedResumeResponse> deleteResume(
      @PathVariable("resumeId") String resumeId,
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid,
      @RequestHeader(AUTHORIZATION_HEADER) String authorization);

  @GetMapping("/dmz/user/application")
  GlobalRestResponse<PagedResponse<UserApplicationResponse>> getUserApplications(
      @RequestParam("page") Integer page,
      @RequestParam("size") Integer size,
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid,
      @RequestHeader(AUTHORIZATION_HEADER) String authorization);

  @GetMapping("/dmz/user/upskilling-suggestion/{jobPostId}")
  GlobalRestResponse<UpskillingSuggestionResponse> suggestUpskillingForUser(
      @PathVariable("jobPostId") String jobPostId,
      @RequestParam("language") String language,
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid,
      @RequestHeader(AUTHORIZATION_HEADER) String authorization);

  @GetMapping("/dmz/user/{userId}")
  GlobalRestResponse<GetUserProfileResponse> getUserProfile(
      @PathVariable("userId") String userId,
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid,
      @RequestHeader(AUTHORIZATION_HEADER) String authorization);

  @PostMapping("/dmz/company")
  GlobalRestResponse<CreateUserWithCompanyResponse> createUserWithCompany(
      @RequestBody CreateUserWithCompanyRequest request,
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid,
      @RequestHeader(AUTHORIZATION_HEADER) String authorization);

  @PatchMapping("/dmz/company")
  GlobalRestResponse<UpdateCompanyResponse> updateCompany(
      @RequestBody UpdateCompanyRequest request,
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid,
      @RequestHeader(AUTHORIZATION_HEADER) String authorization);

  @GetMapping("/dmz/company/generate-upload-image")
  GlobalRestResponse<GenerateUploadImageResponse> generateCompanyImageUploadUrl(
      @RequestParam("file-name") String fileName,
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid,
      @RequestHeader(AUTHORIZATION_HEADER) String authorization);

  @PostMapping("/dmz/company/confirm-upload-image")
  void confirmCompanyImageUpload(
      @RequestParam("file-id") String fileId,
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid,
      @RequestHeader(AUTHORIZATION_HEADER) String authorization);

  @GetMapping("/dmz/company/{companyId}")
  GlobalRestResponse<GetCompanyDetailResponse> getCompanyDetail(
      @PathVariable("companyId") String companyId,
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid,
      @RequestHeader(AUTHORIZATION_HEADER) String authorization);

  @PostMapping("/dmz/job")
  GlobalRestResponse<CreateJobResponse> createJob(
      @RequestBody CreateJobRequest request,
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid,
      @RequestHeader(AUTHORIZATION_HEADER) String authorization);

  @PatchMapping("/dmz/job/{jobId}")
  GlobalRestResponse<UpdateJobResponse> updateJob(
      @PathVariable("jobId") String jobId,
      @RequestBody UpdateJobRequest request,
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid,
      @RequestHeader(AUTHORIZATION_HEADER) String authorization);

  @GetMapping("/dmz/job")
  GlobalRestResponse<PagedResponse<GetJobResponse>> getJobs(
      @RequestParam("page") Integer page,
      @RequestParam("size") Integer size,
      @RequestParam(name = "filterValue", required = false) String filterValue,
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid,
      @RequestHeader(AUTHORIZATION_HEADER) String authorization);

  @GetMapping("/dmz/job/mine")
  GlobalRestResponse<PagedResponse<GetJobResponse>> getMyJobs(
      @RequestParam("page") Integer page,
      @RequestParam("size") Integer size,
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid,
      @RequestHeader(AUTHORIZATION_HEADER) String authorization);

  @GetMapping("/dmz/job/{jobId}/details")
  GlobalRestResponse<JobDetailsResponse> getJobDetails(
      @PathVariable("jobId") String jobId,
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid,
      @RequestHeader(AUTHORIZATION_HEADER) String authorization);

  @PatchMapping("/dmz/job/{jobId}/increase-seen")
  GlobalRestResponse<Long> increaseSeen(
      @PathVariable("jobId") String jobId,
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid,
      @RequestHeader(AUTHORIZATION_HEADER) String authorization);

  @PatchMapping("/dmz/job/{jobId}/refresh")
  GlobalRestResponse<RefreshJobResponse> refreshJob(
      @PathVariable("jobId") String jobId,
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid,
      @RequestHeader(AUTHORIZATION_HEADER) String authorization);

  @PatchMapping("/dmz/job/{jobId}/close")
  GlobalRestResponse<CloseJobResponse> closeJob(
      @PathVariable("jobId") String jobId,
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid,
      @RequestHeader(AUTHORIZATION_HEADER) String authorization);

  @PostMapping("/dmz/job/{jobId}/apply")
  GlobalRestResponse<ApplyJobResponse> applyToJob(
      @PathVariable("jobId") String jobId,
      @RequestBody ApplyJobRequest request,
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid,
      @RequestHeader(AUTHORIZATION_HEADER) String authorization);

  @GetMapping("/dmz/job/{jobId}/candidates")
  GlobalRestResponse<PagedResponse<JobCandidateResponse>> getJobCandidates(
      @PathVariable("jobId") String jobId,
      @RequestParam("page") Integer page,
      @RequestParam("pageSize") Integer pageSize,
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid,
      @RequestHeader(AUTHORIZATION_HEADER) String authorization);

  @GetMapping("/dmz/job/{jobId}/candidate/{candidateId}/candidate-explanation")
  GlobalRestResponse<CandidateExplanationResponse> explainCandidate(
      @PathVariable("jobId") String jobId,
      @PathVariable("candidateId") String candidateId,
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid,
      @RequestHeader(AUTHORIZATION_HEADER) String authorization);

  @GetMapping("/dmz/job/{jobId}/upskilling-suggestion")
  GlobalRestResponse<UpskillingSuggestionResponse> suggestUpskillingForJob(
      @PathVariable("jobId") String jobId,
      @RequestParam("language") String language,
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid,
      @RequestHeader(AUTHORIZATION_HEADER) String authorization);
}
