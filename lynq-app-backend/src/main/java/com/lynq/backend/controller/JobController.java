package com.lynq.backend.controller;

import com.lynq.backend.controller.request.CreateJobRequest;
import com.lynq.backend.controller.response.CreateJobRestResponse;
import com.lynq.backend.controller.response.GetJobRestResponse;
import com.lynq.backend.controller.response.GlobalRestResponse;
import com.lynq.backend.controller.response.PagedRestResponse;
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

}