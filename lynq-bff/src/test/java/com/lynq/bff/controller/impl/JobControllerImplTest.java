package com.lynq.bff.controller.impl;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import com.lynq.bff.controller.response.GlobalRestResponse;
import com.lynq.bff.security.LynqUserPrincipal;
import com.lynq.bff.service.Caller;
import com.lynq.bff.service.JobService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

@ExtendWith(MockitoExtension.class)
class JobControllerImplTest {

  private static final String USER_ID = "11111111-1111-1111-1111-111111111111";
  private static final String REQUEST_UUID = "018f9c3a-2b1d-7c4e-9a6f-1e2d3c4b5a99";
  private static final String AUTHORIZATION = "Bearer access-token";
  private static final String JOB_ID = "018f9c3a-2b1d-7c4e-9a6f-1e2d3c4b5a61";
  private static final String CANDIDATE_ID = "22222222-2222-2222-2222-222222222222";

  private static final LynqUserPrincipal PRINCIPAL = new LynqUserPrincipal(
      USER_ID, "janedoe", "jane@lynq.com",
      List.of(new SimpleGrantedAuthority("R_CANDIDATE")), AUTHORIZATION);

  @Mock
  private JobService jobService;

  private JobControllerImpl jobController;

  @BeforeEach
  void setUp() {
    jobController = new JobControllerImpl(jobService);
  }

  @Test
  void answersACreatedJobPostWithCreated() {
    CreateJobRequest request = CreateJobRequest.builder().title("Backend Engineer").build();
    CreateJobResponse created = CreateJobResponse.builder().jobId(JOB_ID).build();
    when(jobService.createJob(eq(request), any())).thenReturn(created);

    ResponseEntity<GlobalRestResponse<CreateJobResponse>> response =
        jobController.createJob(request, REQUEST_UUID, PRINCIPAL);

    assertThat(response.getStatusCode(), is(HttpStatus.CREATED));
    assertThat(response.getBody().getData(), is(sameInstance(created)));
  }

  @Test
  void callsTheServiceAsTheVerifiedCaller() {
    CreateJobRequest request = CreateJobRequest.builder().title("Backend Engineer").build();

    jobController.createJob(request, REQUEST_UUID, PRINCIPAL);

    ArgumentCaptor<Caller> caller = ArgumentCaptor.forClass(Caller.class);
    verify(jobService).createJob(eq(request), caller.capture());
    assertThat(caller.getValue().userId(), is(USER_ID));
    assertThat(caller.getValue().requestUuid(), is(REQUEST_UUID));
    assertThat(caller.getValue().authorization(), is(AUTHORIZATION));
  }

  @Test
  void answersAnUpdatedJobPostWithOk() {
    UpdateJobRequest request = UpdateJobRequest.builder().title("Staff Engineer").build();
    UpdateJobResponse updated = UpdateJobResponse.builder().jobId(JOB_ID).build();
    when(jobService.updateJob(eq(JOB_ID), eq(request), any())).thenReturn(updated);

    ResponseEntity<GlobalRestResponse<UpdateJobResponse>> response =
        jobController.updateJob(JOB_ID, request, REQUEST_UUID, PRINCIPAL);

    assertThat(response.getStatusCode(), is(HttpStatus.OK));
    assertThat(response.getBody().getData(), is(sameInstance(updated)));
  }

  @Test
  void answersTheJobSearchWithOk() {
    PagedResponse<GetJobResponse> page = PagedResponse.<GetJobResponse>builder().page(1).build();
    when(jobService.getJobs(eq(1), eq(20), eq("kafka"), any())).thenReturn(page);

    ResponseEntity<GlobalRestResponse<PagedResponse<GetJobResponse>>> response =
        jobController.getJobs(1, 20, "kafka", REQUEST_UUID, PRINCIPAL);

    assertThat(response.getStatusCode(), is(HttpStatus.OK));
    assertThat(response.getBody().getData(), is(sameInstance(page)));
  }

  @Test
  void searchesWithoutAFilterWhenTheCallerGivesNone() {
    jobController.getJobs(0, 10, null, REQUEST_UUID, PRINCIPAL);

    verify(jobService).getJobs(eq(0), eq(10), isNull(), any());
  }

  @Test
  void answersTheCompanysJobPostsWithOk() {
    PagedResponse<GetJobResponse> page = PagedResponse.<GetJobResponse>builder().build();
    when(jobService.getMyJobs(eq(0), eq(10), any())).thenReturn(page);

    ResponseEntity<GlobalRestResponse<PagedResponse<GetJobResponse>>> response =
        jobController.getMyJobs(0, 10, REQUEST_UUID, PRINCIPAL);

    assertThat(response.getStatusCode(), is(HttpStatus.OK));
    assertThat(response.getBody().getData(), is(sameInstance(page)));
  }

  @Test
  void answersTheJobDetailWithOk() {
    JobDetailsResponse job = JobDetailsResponse.builder().jobId(JOB_ID).build();
    when(jobService.getJobDetails(eq(JOB_ID), any())).thenReturn(job);

    ResponseEntity<GlobalRestResponse<JobDetailsResponse>> response =
        jobController.getJobDetails(JOB_ID, REQUEST_UUID, PRINCIPAL);

    assertThat(response.getStatusCode(), is(HttpStatus.OK));
    assertThat(response.getBody().getData(), is(sameInstance(job)));
  }

  @Test
  void answersTheNewViewCountWithOk() {
    when(jobService.increaseSeen(eq(JOB_ID), any())).thenReturn(42L);

    ResponseEntity<GlobalRestResponse<Long>> response =
        jobController.increaseSeen(JOB_ID, REQUEST_UUID, PRINCIPAL);

    assertThat(response.getStatusCode(), is(HttpStatus.OK));
    assertThat(response.getBody().getData(), is(42L));
  }

  @Test
  void answersARefreshedJobPostWithOk() {
    RefreshJobResponse refreshed = RefreshJobResponse.builder().jobId(JOB_ID).build();
    when(jobService.refreshJob(eq(JOB_ID), any())).thenReturn(refreshed);

    ResponseEntity<GlobalRestResponse<RefreshJobResponse>> response =
        jobController.refreshJob(JOB_ID, REQUEST_UUID, PRINCIPAL);

    assertThat(response.getStatusCode(), is(HttpStatus.OK));
    assertThat(response.getBody().getData(), is(sameInstance(refreshed)));
  }

  @Test
  void answersAClosedJobPostWithOk() {
    CloseJobResponse closed = CloseJobResponse.builder().jobId(JOB_ID).build();
    when(jobService.closeJob(eq(JOB_ID), any())).thenReturn(closed);

    ResponseEntity<GlobalRestResponse<CloseJobResponse>> response =
        jobController.closeJob(JOB_ID, REQUEST_UUID, PRINCIPAL);

    assertThat(response.getStatusCode(), is(HttpStatus.OK));
    assertThat(response.getBody().getData(), is(sameInstance(closed)));
  }

  @Test
  void answersAnApplicationWithCreated() {
    ApplyJobRequest request = ApplyJobRequest.builder().resumeId("resume-1").build();
    ApplyJobResponse applied = ApplyJobResponse.builder().jobId(JOB_ID).build();
    when(jobService.applyToJob(eq(JOB_ID), eq(request), any())).thenReturn(applied);

    ResponseEntity<GlobalRestResponse<ApplyJobResponse>> response =
        jobController.applyToJob(JOB_ID, request, REQUEST_UUID, PRINCIPAL);

    assertThat(response.getStatusCode(), is(HttpStatus.CREATED));
    assertThat(response.getBody().getData(), is(sameInstance(applied)));
  }

  @Test
  void answersTheCandidatesWithOk() {
    PagedResponse<JobCandidateResponse> page =
        PagedResponse.<JobCandidateResponse>builder().build();
    when(jobService.getCandidates(eq(JOB_ID), eq(3), eq(25), any())).thenReturn(page);

    ResponseEntity<GlobalRestResponse<PagedResponse<JobCandidateResponse>>> response =
        jobController.getCandidates(JOB_ID, 3, 25, REQUEST_UUID, PRINCIPAL);

    assertThat(response.getStatusCode(), is(HttpStatus.OK));
    assertThat(response.getBody().getData(), is(sameInstance(page)));
  }

  @Test
  void answersTheCandidateExplanationWithOk() {
    CandidateExplanationResponse explanation =
        CandidateExplanationResponse.builder().recommendation("HIRE").build();
    when(jobService.explainCandidate(eq(JOB_ID), eq(CANDIDATE_ID), any())).thenReturn(explanation);

    ResponseEntity<GlobalRestResponse<CandidateExplanationResponse>> response = jobController
        .explainCandidate(JOB_ID, CANDIDATE_ID, REQUEST_UUID, PRINCIPAL);

    assertThat(response.getStatusCode(), is(HttpStatus.OK));
    assertThat(response.getBody().getData(), is(sameInstance(explanation)));
  }

  @Test
  void answersTheUpskillingSuggestionWithOk() {
    UpskillingSuggestionResponse suggestion =
        UpskillingSuggestionResponse.builder().outcome("GAPS").build();
    when(jobService.suggestUpskilling(eq(JOB_ID), eq("es"), any())).thenReturn(suggestion);

    ResponseEntity<GlobalRestResponse<UpskillingSuggestionResponse>> response =
        jobController.suggestUpskilling(JOB_ID, "es", REQUEST_UUID, PRINCIPAL);

    assertThat(response.getStatusCode(), is(HttpStatus.OK));
    assertThat(response.getBody().getData(), is(sameInstance(suggestion)));
  }
}
