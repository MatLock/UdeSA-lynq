package com.lynq.backend.controller.impl;

import com.lynq.backend.controller.request.IngestJobPostRequest;
import com.lynq.backend.controller.request.IngestJobPostsRequest;
import com.lynq.backend.controller.response.GlobalRestResponse;
import com.lynq.backend.controller.response.IngestJobPostsRestResponse;
import com.lynq.backend.enums.JobPostSource;
import com.lynq.backend.enums.WorkType;
import com.lynq.backend.service.JobIngestService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InternalJobIngestControllerImplTest {

  private static final String EXTERNAL_ID = "1118437287";
  private static final String TITLE = "DevSecOps Senior";
  private static final int INGESTED_JOBS = 10;
  private static final int INGESTED_COMPANIES = 4;
  private static final int INGESTED_SKILLS = 30;
  private static final int INGESTED_SIMILARITY_TAGS = 18;
  private static final int SKIPPED = 1;

  @Mock
  private JobIngestService jobIngestService;

  private InternalJobIngestControllerImpl controller;

  @BeforeEach
  void setUp() {
    controller = new InternalJobIngestControllerImpl(jobIngestService);
  }

  private IngestJobPostsRequest requestWith(IngestJobPostRequest... jobPosts) {
    IngestJobPostsRequest request = new IngestJobPostsRequest();
    request.setJobPosts(List.of(jobPosts));
    return request;
  }

  private IngestJobPostRequest jobPost() {
    return IngestJobPostRequest.builder()
        .externalId(EXTERNAL_ID)
        .title(TITLE)
        .workType(WorkType.REMOTE)
        .jobPostSource(JobPostSource.BUMERAN)
        .build();
  }

  private IngestJobPostsRestResponse stats() {
    return IngestJobPostsRestResponse.builder()
        .jobs(INGESTED_JOBS)
        .companies(INGESTED_COMPANIES)
        .skills(INGESTED_SKILLS)
        .similarityTags(INGESTED_SIMILARITY_TAGS)
        .skipped(SKIPPED)
        .build();
  }

  @Test
  void ingestReturnsOkWithTheStatsInsideTheStandardEnvelope() {
    when(jobIngestService.ingest(anyList())).thenReturn(stats());

    ResponseEntity<GlobalRestResponse<IngestJobPostsRestResponse>> response =
        controller.ingest(requestWith(jobPost()));

    assertThat(response.getStatusCode(), is(HttpStatus.OK));
    assertThat(response.getBody(), is(notNullValue()));
    assertThat(response.getBody().isSuccess(), is(true));
    assertThat(response.getBody().getData().getJobs(), is(INGESTED_JOBS));
    assertThat(response.getBody().getData().getSimilarityTags(), is(INGESTED_SIMILARITY_TAGS));
    assertThat(response.getBody().getData().getSkipped(), is(SKIPPED));
  }

  @Test
  void ingestDelegatesTheWholeBatchToTheService() {
    when(jobIngestService.ingest(anyList())).thenReturn(stats());
    ArgumentCaptor<List<IngestJobPostRequest>> captor = ArgumentCaptor.forClass(List.class);

    controller.ingest(requestWith(jobPost(), jobPost()));

    verify(jobIngestService).ingest(captor.capture());
    assertThat(captor.getValue(), hasSize(2));
    assertThat(captor.getValue().get(0).getExternalId(), is(EXTERNAL_ID));
  }
}
