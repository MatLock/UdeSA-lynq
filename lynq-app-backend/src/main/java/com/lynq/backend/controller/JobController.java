package com.lynq.backend.controller;

import com.lynq.backend.client.response.CandidateExplanationResponse;
import com.lynq.backend.client.response.UpskillingSuggestionResponse;
import com.lynq.backend.controller.request.CreateJobRequest;
import com.lynq.backend.controller.request.UpdateJobRequest;
import com.lynq.backend.controller.response.ApplyJobRestResponse;
import com.lynq.backend.controller.response.CloseJobRestResponse;
import com.lynq.backend.controller.response.CreateJobRestResponse;
import com.lynq.backend.controller.response.GetJobDetailForCandidateRestResponse;
import com.lynq.backend.controller.response.GetJobRestResponse;
import com.lynq.backend.controller.response.JobCandidateResponse;
import com.lynq.backend.controller.response.GlobalRestResponse;
import com.lynq.backend.controller.response.PagedRestResponse;
import com.lynq.backend.controller.response.RefreshJobRestResponse;
import com.lynq.backend.controller.response.UpdateJobRestResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;

@Tag(name = "Job", description = "Operations for managing Lynq platform job posts")
public interface JobController {

  @Operation(
      summary = "Create a job post",
      description = "Creates a job post for the company owned by the authenticated user. The owner "
          + "identity is resolved from the bearer token and must be a COMPANY-type user linked to "
          + "a company.",
      security = @SecurityRequirement(name = "bearerAuth"))
  ResponseEntity<GlobalRestResponse<CreateJobRestResponse>> createJob(
      @Valid CreateJobRequest request);

  @Operation(
      summary = "Update a job post",
      description = "Updates the editable fields (title, description, work type, status, salary "
          + "range and skills) of the job post identified by the given id. Only the user who "
          + "created the job post may update it; the skills provided fully replace the existing "
          + "ones and changing the status stamps or clears the closed date accordingly. Fails with "
          + "404 when no job post matches the id and 403 when the caller is not the owner.",
      security = @SecurityRequirement(name = "bearerAuth"))
  ResponseEntity<GlobalRestResponse<UpdateJobRestResponse>> updateJob(
      String jobId,
      @Valid UpdateJobRequest request);

  @Operation(
      summary = "List available job posts",
      description = "Returns a page of available job posts, newest first (ordered by creation date "
          + "descending). Jobs that are no longer available are skipped. Each item includes the "
          + "company and the user who created the post. Results can be filtered with a single "
          + "free-text 'filterValue' matched (case-insensitive, contains) against any of the "
          + "title, description, company name, work type or skill columns. An omitted or blank "
          + "filterValue returns all available jobs.",
      security = @SecurityRequirement(name = "bearerAuth"))
  ResponseEntity<GlobalRestResponse<PagedRestResponse<GetJobRestResponse>>> getJobs(
      Integer page,
      Integer size,
      String filterValue);

  @Operation(
      summary = "List the authenticated owner's job posts",
      description = "Returns a page of the job posts created by the authenticated user, newest first "
          + "(ordered by creation date descending), using the same response shape as the public "
          + "listing (company, poster and skills). Unlike the public feed this includes job posts "
          + "in any status. Only COMPANY-type users may call it; the owner identity is resolved "
          + "from the bearer token. Fails with 400 when the caller is not a COMPANY-type user.",
      security = @SecurityRequirement(name = "bearerAuth"))
  ResponseEntity<GlobalRestResponse<PagedRestResponse<GetJobRestResponse>>> getMyJobs(
      Integer page,
      Integer size);

  @Operation(
      summary = "Get a single job post's details",
      description = "Returns the details of the job post identified by the given id, using the same "
          + "response shape as the paginated listing (company, poster, skills and the LyNQ score "
          + "for the authenticated user). Fails with 404 when no job post matches the id.",
      security = @SecurityRequirement(name = "bearerAuth"))
  ResponseEntity<GlobalRestResponse<GetJobDetailForCandidateRestResponse>> getJobDetails(
      String jobId);

  @Operation(
      summary = "Increase the seen counter of a job post",
      description = "Atomically increments the total seen counter of the job post identified by "
          + "the given id and returns the updated counter value. Fails with 404 when no job post "
          + "matches the id.",
      security = @SecurityRequirement(name = "bearerAuth"))
  ResponseEntity<GlobalRestResponse<Long>> increaseSeen(String jobId);

  @Operation(
      summary = "Refresh (reopen) a closed job post",
      description = "Reopens the closed job post identified by the given id, setting its status back "
          + "to OPEN and stamping its creation date with the current date so it resurfaces at the "
          + "top of the feed. Only the user who created the job post may refresh it. Fails with 404 "
          + "when no job post matches the id, 403 when the caller is not the owner, and 400 when the "
          + "job post is not currently closed.",
      security = @SecurityRequirement(name = "bearerAuth"))
  ResponseEntity<GlobalRestResponse<RefreshJobRestResponse>> refreshJob(String jobId);

  @Operation(
      summary = "Close an open job post",
      description = "Closes the open job post identified by the given id, setting its status to "
          + "CLOSE and stamping the date it was closed. Only the user who created the job post may "
          + "close it. Fails with 404 when no job post matches the id, 403 when the caller is not "
          + "the owner, and 400 when the job post is not currently open.",
      security = @SecurityRequirement(name = "bearerAuth"))
  ResponseEntity<GlobalRestResponse<CloseJobRestResponse>> closeJob(String jobId);

  @Operation(
      summary = "Apply to a job post",
      description = "Registers an application of the authenticated user to the job post identified "
          + "by the given id. The applicant identity is resolved from the bearer token. Fails with "
          + "404 when no job post matches the id, and with 400 when the user has already applied to "
          + "the same job.",
      security = @SecurityRequirement(name = "bearerAuth"))
  ResponseEntity<GlobalRestResponse<ApplyJobRestResponse>> applyToJob(String jobId);

  @Operation(
      summary = "List the candidates that applied to a job post",
      description = "Returns a page of the candidates who applied to the job post identified by the "
          + "given id, newest application first. Each item includes the applicant's public profile "
          + "fields and the date the application was submitted. Pagination is controlled with the "
          + "'page' (zero-based) and 'pageSize' (default 10) request parameters. Only the user who "
          + "created the job post may list its candidates. Fails with 404 when no job post matches "
          + "the id, and 403 when the caller is not the owner.",
      security = @SecurityRequirement(name = "bearerAuth"))
  ResponseEntity<GlobalRestResponse<PagedRestResponse<JobCandidateResponse>>> getJobCandidates(
      String jobId,
      Integer page,
      Integer pageSize);

  @Operation(
      summary = "Explain whether a candidate should be hired for a job post",
      description = "Returns an AI hiring recommendation (with the reasons for and against hiring) "
          + "for the candidate identified by 'candidateId' against the job post identified by "
          + "'jobId'. The job and candidate information is read from the database and forwarded to "
          + "the lynq-ml service. Only the COMPANY-type owner of both the company and the job post "
          + "may request it. Fails with 404 when the job post or candidate does not exist, and 403 "
          + "when the caller is not the owner of the job post.",
      security = @SecurityRequirement(name = "bearerAuth"))
  ResponseEntity<GlobalRestResponse<CandidateExplanationResponse>> explainCandidate(
      String jobId,
      String candidateId,
      String requestUuid);

  @Operation(
      summary = "Suggest upskilling courses for the authenticated candidate against a job post",
      description = "Returns an AI upskilling recommendation (a verdict plus course suggestions) "
          + "for the authenticated user against the job post identified by 'jobId'. The user and "
          + "job information is read from the database and forwarded to the lynq-ml service. Only "
          + "CANDIDATE-type users may call it; the caller identity is resolved from the bearer "
          + "token. Fails with 400 when the caller is not a CANDIDATE and 404 when the job post "
          + "does not exist.",
      security = @SecurityRequirement(name = "bearerAuth"))
  ResponseEntity<GlobalRestResponse<UpskillingSuggestionResponse>> suggestUpskilling(
      String jobId,
      String requestUuid);

}