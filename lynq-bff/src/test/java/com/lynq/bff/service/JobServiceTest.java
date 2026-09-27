package com.lynq.bff.service;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

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
import com.lynq.bff.controller.response.GlobalRestResponse;
import com.lynq.bff.exceptions.BadGatewayException;
import com.lynq.bff.exceptions.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JobServiceTest {

  private static final String USER_ID = "11111111-1111-1111-1111-111111111111";
  private static final String REQUEST_UUID = "018f9c3a-2b1d-7c4e-9a6f-1e2d3c4b5a99";
  private static final String AUTHORIZATION = "Bearer access-token";
  private static final Caller CALLER = new Caller(USER_ID, REQUEST_UUID, AUTHORIZATION);

  private static final String JOB_ID = "018f9c3a-2b1d-7c4e-9a6f-1e2d3c4b5a61";
  private static final String CANDIDATE_ID = "22222222-2222-2222-2222-222222222222";
  private static final String LANGUAGE = "es";

  @Mock
  private LynqBackendClient lynqBackendClient;

  private JobService jobService;

  @BeforeEach
  void setUp() {
    jobService = new JobService(lynqBackendClient);
  }

  @Test
  void createsAJobPost() {
    CreateJobRequest request = CreateJobRequest.builder().title("Backend Engineer").build();
    CreateJobResponse created = CreateJobResponse.builder().jobId(JOB_ID).build();
    when(lynqBackendClient.createJob(request, REQUEST_UUID, AUTHORIZATION))
        .thenReturn(new GlobalRestResponse<>(true, created));

    assertThat(jobService.createJob(request, CALLER), is(sameInstance(created)));
  }

  @Test
  void updatesAJobPost() {
    UpdateJobRequest request = UpdateJobRequest.builder().title("Staff Engineer").build();
    UpdateJobResponse updated = UpdateJobResponse.builder().jobId(JOB_ID).build();
    when(lynqBackendClient.updateJob(JOB_ID, request, REQUEST_UUID, AUTHORIZATION))
        .thenReturn(new GlobalRestResponse<>(true, updated));

    assertThat(jobService.updateJob(JOB_ID, request, CALLER), is(sameInstance(updated)));
  }

  @Test
  void searchesTheJobPostsWithTheFilterAndThePaging() {
    PagedResponse<GetJobResponse> page =
        PagedResponse.<GetJobResponse>builder().page(1).size(20).build();
    when(lynqBackendClient.getJobs(1, 20, "kafka", REQUEST_UUID, AUTHORIZATION))
        .thenReturn(new GlobalRestResponse<>(true, page));

    assertThat(jobService.getJobs(1, 20, "kafka", CALLER), is(sameInstance(page)));
  }

  @Test
  void searchesWithoutAFilterWhenTheCallerGivesNone() {
    PagedResponse<GetJobResponse> page = PagedResponse.<GetJobResponse>builder().build();
    when(lynqBackendClient.getJobs(0, 10, null, REQUEST_UUID, AUTHORIZATION))
        .thenReturn(new GlobalRestResponse<>(true, page));

    assertThat(jobService.getJobs(0, 10, null, CALLER), is(sameInstance(page)));
  }

  @Test
  void listsTheJobPostsOfTheCallersCompany() {
    PagedResponse<GetJobResponse> page = PagedResponse.<GetJobResponse>builder().build();
    when(lynqBackendClient.getMyJobs(0, 10, REQUEST_UUID, AUTHORIZATION))
        .thenReturn(new GlobalRestResponse<>(true, page));

    assertThat(jobService.getMyJobs(0, 10, CALLER), is(sameInstance(page)));
  }

  @Test
  void readsAJobPostDetail() {
    JobDetailsResponse job = JobDetailsResponse.builder().jobId(JOB_ID).build();
    when(lynqBackendClient.getJobDetails(JOB_ID, REQUEST_UUID, AUTHORIZATION))
        .thenReturn(new GlobalRestResponse<>(true, job));

    assertThat(jobService.getJobDetails(JOB_ID, CALLER), is(sameInstance(job)));
  }

  @Test
  void increasesTheViewCount() {
    when(lynqBackendClient.increaseSeen(JOB_ID, REQUEST_UUID, AUTHORIZATION))
        .thenReturn(new GlobalRestResponse<>(true, 42L));

    assertThat(jobService.increaseSeen(JOB_ID, CALLER), is(42L));
  }

  @Test
  void refreshesAJobPost() {
    RefreshJobResponse refreshed = RefreshJobResponse.builder().jobId(JOB_ID).build();
    when(lynqBackendClient.refreshJob(JOB_ID, REQUEST_UUID, AUTHORIZATION))
        .thenReturn(new GlobalRestResponse<>(true, refreshed));

    assertThat(jobService.refreshJob(JOB_ID, CALLER), is(sameInstance(refreshed)));
  }

  @Test
  void closesAJobPost() {
    CloseJobResponse closed = CloseJobResponse.builder().jobId(JOB_ID).build();
    when(lynqBackendClient.closeJob(JOB_ID, REQUEST_UUID, AUTHORIZATION))
        .thenReturn(new GlobalRestResponse<>(true, closed));

    assertThat(jobService.closeJob(JOB_ID, CALLER), is(sameInstance(closed)));
  }

  @Test
  void appliesToAJobPost() {
    ApplyJobRequest request = ApplyJobRequest.builder().resumeId("resume-1").build();
    ApplyJobResponse applied = ApplyJobResponse.builder().jobId(JOB_ID).build();
    when(lynqBackendClient.applyToJob(JOB_ID, request, REQUEST_UUID, AUTHORIZATION))
        .thenReturn(new GlobalRestResponse<>(true, applied));

    assertThat(jobService.applyToJob(JOB_ID, request, CALLER), is(sameInstance(applied)));
  }

  /** The candidate list pages on {@code pageSize}, not on the {@code size} the other routes use. */
  @Test
  void listsTheCandidatesWithTheirOwnPageSizeParameter() {
    PagedResponse<JobCandidateResponse> page =
        PagedResponse.<JobCandidateResponse>builder().size(25).build();
    when(lynqBackendClient.getJobCandidates(JOB_ID, 3, 25, REQUEST_UUID, AUTHORIZATION))
        .thenReturn(new GlobalRestResponse<>(true, page));

    assertThat(jobService.getCandidates(JOB_ID, 3, 25, CALLER), is(sameInstance(page)));
  }

  @Test
  void explainsACandidate() {
    CandidateExplanationResponse explanation =
        CandidateExplanationResponse.builder().recommendation("HIRE").build();
    when(lynqBackendClient.explainCandidate(JOB_ID, CANDIDATE_ID, REQUEST_UUID, AUTHORIZATION))
        .thenReturn(new GlobalRestResponse<>(true, explanation));

    assertThat(jobService.explainCandidate(JOB_ID, CANDIDATE_ID, CALLER),
        is(sameInstance(explanation)));
  }

  @Test
  void suggestsUpskillingForAJobPost() {
    UpskillingSuggestionResponse suggestion =
        UpskillingSuggestionResponse.builder().outcome("GAPS").build();
    when(lynqBackendClient.suggestUpskillingForJob(JOB_ID, LANGUAGE, REQUEST_UUID, AUTHORIZATION))
        .thenReturn(new GlobalRestResponse<>(true, suggestion));

    assertThat(jobService.suggestUpskilling(JOB_ID, LANGUAGE, CALLER), is(sameInstance(suggestion)));
  }

  @Test
  void keepsTheNotFoundOfAJobPostThatDoesNotExist() {
    when(lynqBackendClient.getJobDetails("nope", REQUEST_UUID, AUTHORIZATION))
        .thenThrow(FeignErrors.status(404, """
            {"success": false, "reason": "Job post not found"}"""));

    NotFoundException thrown = assertThrows(NotFoundException.class,
        () -> jobService.getJobDetails("nope", CALLER));

    assertThat(thrown.getMessage(), is("Job post not found"));
  }

  @Test
  void answersBadGatewayWhenLynqBackendFailsOnItsOwnAccount() {
    when(lynqBackendClient.closeJob(JOB_ID, REQUEST_UUID, AUTHORIZATION))
        .thenThrow(FeignErrors.status(500, "boom"));

    BadGatewayException thrown =
        assertThrows(BadGatewayException.class, () -> jobService.closeJob(JOB_ID, CALLER));

    assertThat(thrown.getMessage(), is("The job post could not be closed"));
  }
}
