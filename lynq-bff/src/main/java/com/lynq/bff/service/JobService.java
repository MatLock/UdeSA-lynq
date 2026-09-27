package com.lynq.bff.service;

import com.lynq.bff.client.LynqBackendClient;
import com.lynq.bff.client.request.ApplyJobRequest;
import com.lynq.bff.client.request.CreateJobRequest;
import com.lynq.bff.client.request.UpdateJobRequest;
import com.lynq.bff.client.response.ApplyJobResponse;
import com.lynq.bff.client.response.CandidateExplanationResponse;
import com.lynq.bff.client.response.CloseJobResponse;
import com.lynq.bff.client.response.CreateJobResponse;
import com.lynq.bff.client.response.GetJobResponse;
import com.lynq.bff.client.response.JobCandidateResponse;
import com.lynq.bff.client.response.JobDetailsResponse;
import com.lynq.bff.client.response.PagedResponse;
import com.lynq.bff.client.response.RefreshJobResponse;
import com.lynq.bff.client.response.UpdateJobResponse;
import com.lynq.bff.client.response.UpskillingSuggestionResponse;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

@Service
@Log4j2
public class JobService {

  private static final String JOB_NOT_CREATED = "The job post could not be created";
  private static final String JOB_NOT_UPDATED = "The job post could not be updated";
  private static final String JOBS_UNREADABLE = "The job posts could not be read";
  private static final String JOB_UNREADABLE = "The job post could not be read";
  private static final String SEEN_NOT_INCREASED = "The job post views could not be increased";
  private static final String JOB_NOT_REFRESHED = "The job post could not be refreshed";
  private static final String JOB_NOT_CLOSED = "The job post could not be closed";
  private static final String APPLICATION_NOT_REGISTERED = "The application could not be registered";
  private static final String CANDIDATES_UNREADABLE = "The candidates could not be read";
  private static final String EXPLANATION_UNREADABLE = "The candidate explanation could not be read";
  private static final String SUGGESTION_UNREADABLE = "The upskilling suggestion could not be read";

  private final LynqBackendClient lynqBackendClient;

  public JobService(LynqBackendClient lynqBackendClient) {
    this.lynqBackendClient = lynqBackendClient;
  }

  public CreateJobResponse createJob(CreateJobRequest request, Caller caller) {
    log.info("message= Creating job post, user_id={}, title={}",
        caller.userId(), request.getTitle());

    return DownstreamErrors.call(
        () -> lynqBackendClient
            .createJob(request, caller.requestUuid(), caller.authorization())
            .getData(),
        JOB_NOT_CREATED);
  }

  public UpdateJobResponse updateJob(String jobId, UpdateJobRequest request, Caller caller) {
    log.info("message= Updating job post, user_id={}, job_id={}", caller.userId(), jobId);

    return DownstreamErrors.call(
        () -> lynqBackendClient
            .updateJob(jobId, request, caller.requestUuid(), caller.authorization())
            .getData(),
        JOB_NOT_UPDATED);
  }

  public PagedResponse<GetJobResponse> getJobs(Integer page, Integer size, String filterValue,
                                               Caller caller) {
    return DownstreamErrors.call(
        () -> lynqBackendClient
            .getJobs(page, size, filterValue, caller.requestUuid(), caller.authorization())
            .getData(),
        JOBS_UNREADABLE);
  }

  public PagedResponse<GetJobResponse> getMyJobs(Integer page, Integer size, Caller caller) {
    return DownstreamErrors.call(
        () -> lynqBackendClient
            .getMyJobs(page, size, caller.requestUuid(), caller.authorization())
            .getData(),
        JOBS_UNREADABLE);
  }

  public JobDetailsResponse getJobDetails(String jobId, Caller caller) {
    return DownstreamErrors.call(
        () -> lynqBackendClient
            .getJobDetails(jobId, caller.requestUuid(), caller.authorization())
            .getData(),
        JOB_UNREADABLE);
  }

  public Long increaseSeen(String jobId, Caller caller) {
    return DownstreamErrors.call(
        () -> lynqBackendClient
            .increaseSeen(jobId, caller.requestUuid(), caller.authorization())
            .getData(),
        SEEN_NOT_INCREASED);
  }

  public RefreshJobResponse refreshJob(String jobId, Caller caller) {
    log.info("message= Refreshing job post, user_id={}, job_id={}", caller.userId(), jobId);

    return DownstreamErrors.call(
        () -> lynqBackendClient
            .refreshJob(jobId, caller.requestUuid(), caller.authorization())
            .getData(),
        JOB_NOT_REFRESHED);
  }

  public CloseJobResponse closeJob(String jobId, Caller caller) {
    log.info("message= Closing job post, user_id={}, job_id={}", caller.userId(), jobId);

    return DownstreamErrors.call(
        () -> lynqBackendClient
            .closeJob(jobId, caller.requestUuid(), caller.authorization())
            .getData(),
        JOB_NOT_CLOSED);
  }

  public ApplyJobResponse applyToJob(String jobId, ApplyJobRequest request, Caller caller) {
    log.info("message= Applying to job post, user_id={}, job_id={}", caller.userId(), jobId);

    return DownstreamErrors.call(
        () -> lynqBackendClient
            .applyToJob(jobId, request, caller.requestUuid(), caller.authorization())
            .getData(),
        APPLICATION_NOT_REGISTERED);
  }

  public PagedResponse<JobCandidateResponse> getCandidates(String jobId, Integer page,
                                                           Integer pageSize, Caller caller) {
    return DownstreamErrors.call(
        () -> lynqBackendClient
            .getJobCandidates(jobId, page, pageSize, caller.requestUuid(), caller.authorization())
            .getData(),
        CANDIDATES_UNREADABLE);
  }

  public CandidateExplanationResponse explainCandidate(String jobId, String candidateId,
                                                       Caller caller) {
    return DownstreamErrors.call(
        () -> lynqBackendClient
            .explainCandidate(jobId, candidateId, caller.requestUuid(), caller.authorization())
            .getData(),
        EXPLANATION_UNREADABLE);
  }

  public UpskillingSuggestionResponse suggestUpskilling(String jobId, String language,
                                                        Caller caller) {
    return DownstreamErrors.call(
        () -> lynqBackendClient
            .suggestUpskillingForJob(jobId, language, caller.requestUuid(), caller.authorization())
            .getData(),
        SUGGESTION_UNREADABLE);
  }
}
