package com.lynq.backend.service;

import com.lynq.backend.controller.request.IngestJobPostRequest;
import com.lynq.backend.controller.response.IngestJobPostsRestResponse;
import com.lynq.backend.enums.JobPostSource;
import com.lynq.backend.enums.JobStatus;
import com.lynq.backend.enums.WorkType;
import com.lynq.backend.model.CompanyEntity;
import com.lynq.backend.model.JobPostEntity;
import com.lynq.backend.model.JobPostSimilarityTagEntity;
import com.lynq.backend.model.JobPostSkillEntity;
import com.lynq.backend.repository.CompanyRepository;
import com.lynq.backend.repository.JobPostRepository;
import com.lynq.backend.repository.JobPostSimilarityTagRepository;
import com.lynq.backend.repository.JobPostSkillRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.Month;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobIngestServiceTest {

  private static final String EXTERNAL_ID = "1118437287";
  private static final String TITLE = "DevSecOps Senior";
  private static final String DESCRIPTION = "Referente tecnico en iniciativas de DevSecOps.";
  private static final String COMPANY_NAME = "KPMG";
  private static final String JOB_URL = "https://www.bumeran.com.ar/empleos/devsecops-senior-1118437287.html";

  private static final String EXPECTED_JOB_ID = "4b422947-8ced-54da-8c1c-ccbfb064f434";
  private static final String EXPECTED_COMPANY_ID = "2fd4e277-d7fb-5269-b87f-9291f24ad704";

  private static final long POSTED_AT = 1789055415000L;
  private static final LocalDate POSTED_ON = LocalDate.of(2026, Month.SEPTEMBER, 10);

  @Mock
  private JobPostRepository jobPostRepository;

  @Mock
  private CompanyRepository companyRepository;

  @Mock
  private JobPostSkillRepository jobPostSkillRepository;

  @Mock
  private JobPostSimilarityTagRepository jobPostSimilarityTagRepository;

  private JobIngestService service;

  @BeforeEach
  void setUp() {
    service = new JobIngestService(jobPostRepository, companyRepository, jobPostSkillRepository,
        jobPostSimilarityTagRepository);
  }

  private IngestJobPostRequest.IngestJobPostRequestBuilder request() {
    return IngestJobPostRequest.builder()
        .externalId(EXTERNAL_ID)
        .title(TITLE)
        .description(DESCRIPTION)
        .workType(WorkType.REMOTE)
        .jobPostSource(JobPostSource.BUMERAN)
        .companyName(COMPANY_NAME)
        .jobUrl(JOB_URL)
        .postedAt(POSTED_AT)
        .skills(List.of("Python"))
        .similarityTags(List.of("Backend Development"));
  }

  private void savesWhatItIsGiven() {
    when(jobPostRepository.save(any(JobPostEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(companyRepository.save(any(CompanyEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
  }

  @Test
  void ingestCreatesJobCompanySkillsAndSimilarityTags() {
    savesWhatItIsGiven();

    IngestJobPostsRestResponse response = service.ingest(List.of(request().build()));

    assertThat(response.getJobs(), is(1));
    assertThat(response.getCompanies(), is(1));
    assertThat(response.getSkills(), is(1));
    assertThat(response.getSimilarityTags(), is(1));
    assertThat(response.getSkipped(), is(0));
  }

  @Test
  void jobIdIsADeterministicUuidOfSourceAndExternalId() {
    savesWhatItIsGiven();
    ArgumentCaptor<JobPostEntity> captor = ArgumentCaptor.forClass(JobPostEntity.class);

    service.ingest(List.of(request().build()));

    verify(jobPostRepository).save(captor.capture());
    assertThat(captor.getValue().getId(), is(EXPECTED_JOB_ID));
  }

  @Test
  void companyIdIsADeterministicUuidOfItsLowercasedName() {
    savesWhatItIsGiven();
    ArgumentCaptor<CompanyEntity> captor = ArgumentCaptor.forClass(CompanyEntity.class);

    service.ingest(List.of(request().build()));

    verify(companyRepository).save(captor.capture());
    assertThat(captor.getValue().getId(), is(EXPECTED_COMPANY_ID));
    assertThat(captor.getValue().getName(), is(COMPANY_NAME));
  }

  @Test
  void ingestedJobsHaveNoCreatingUserAndOpenStatus() {
    savesWhatItIsGiven();
    ArgumentCaptor<JobPostEntity> captor = ArgumentCaptor.forClass(JobPostEntity.class);

    service.ingest(List.of(request().build()));

    verify(jobPostRepository).save(captor.capture());
    assertThat(captor.getValue().getCreatedByUser(), is(nullValue()));
    assertThat(captor.getValue().getJobStatus(), is(JobStatus.OPEN));
    assertThat(captor.getValue().getTotalSeen(), is(0L));
  }

  @Test
  void createdOnComesFromThePostedAtTimestamp() {
    savesWhatItIsGiven();
    ArgumentCaptor<JobPostEntity> captor = ArgumentCaptor.forClass(JobPostEntity.class);

    service.ingest(List.of(request().build()));

    verify(jobPostRepository).save(captor.capture());
    assertThat(captor.getValue().getCreatedOn(), is(POSTED_ON));
  }

  @Test
  void createdOnFallsBackToTodayWhenPostedAtIsMissing() {
    savesWhatItIsGiven();
    ArgumentCaptor<JobPostEntity> captor = ArgumentCaptor.forClass(JobPostEntity.class);

    service.ingest(List.of(request().postedAt(null).build()));

    verify(jobPostRepository).save(captor.capture());
    assertThat(captor.getValue().getCreatedOn(), is(notNullValue()));
  }

  @Test
  void anExistingJobIsUpdatedInPlaceAndKeepsItsViewCount() {
    JobPostEntity existing = JobPostEntity.builder()
        .id(EXPECTED_JOB_ID)
        .title("Old title")
        .totalSeen(42L)
        .jobStatus(JobStatus.OPEN)
        .build();
    when(jobPostRepository.findById(EXPECTED_JOB_ID)).thenReturn(Optional.of(existing));
    when(jobPostRepository.save(any(JobPostEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(companyRepository.save(any(CompanyEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    service.ingest(List.of(request().build()));

    assertThat(existing.getTitle(), is(TITLE));
    assertThat(existing.getTotalSeen(), is(42L));
  }

  @Test
  void anExistingCompanyIsReusedAndNotCountedAsNew() {
    CompanyEntity existing = CompanyEntity.builder().id(EXPECTED_COMPANY_ID).name(COMPANY_NAME).build();
    when(companyRepository.findById(EXPECTED_COMPANY_ID)).thenReturn(Optional.of(existing));
    when(jobPostRepository.save(any(JobPostEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));

    IngestJobPostsRestResponse response = service.ingest(List.of(request().build()));

    assertThat(response.getCompanies(), is(0));
    verify(companyRepository, never()).save(any(CompanyEntity.class));
  }

  @Test
  void skillsAndTagsAreReplacedRatherThanAppended() {
    savesWhatItIsGiven();
    JobPostSkillEntity stale = JobPostSkillEntity.builder().id("stale").skill("Cobol").build();
    when(jobPostSkillRepository.findByJobPost(any(JobPostEntity.class))).thenReturn(List.of(stale));

    service.ingest(List.of(request().build()));

    verify(jobPostSkillRepository).deleteAll(List.of(stale));
    verify(jobPostSimilarityTagRepository).deleteAll(any());
  }

  @Test
  void similarityTagsArePersisted() {
    savesWhatItIsGiven();
    ArgumentCaptor<List<JobPostSimilarityTagEntity>> captor = ArgumentCaptor.forClass(List.class);

    service.ingest(List.of(request().similarityTags(List.of("Backend Development", "Cloud")).build()));

    verify(jobPostSimilarityTagRepository).saveAll(captor.capture());
    assertThat(captor.getValue(), hasSize(2));
    assertThat(captor.getValue().stream().map(JobPostSimilarityTagEntity::getSimilarityTag).toList(),
        contains("Backend Development", "Cloud"));
  }

  @Test
  void duplicateAndBlankSkillsAreDropped() {
    savesWhatItIsGiven();
    ArgumentCaptor<List<JobPostSkillEntity>> captor = ArgumentCaptor.forClass(List.class);

    service.ingest(List.of(request().skills(List.of("Python", " python ", "  ", "FastAPI")).build()));

    verify(jobPostSkillRepository).saveAll(captor.capture());
    assertThat(captor.getValue().stream().map(JobPostSkillEntity::getSkill).toList(),
        contains("Python", "FastAPI"));
  }

  @Test
  void nullSkillListYieldsNoSkills() {
    savesWhatItIsGiven();

    IngestJobPostsRestResponse response =
        service.ingest(List.of(request().skills(null).similarityTags(null).build()));

    assertThat(response.getSkills(), is(0));
    assertThat(response.getSimilarityTags(), is(0));
  }

  @Test
  void aListingWithoutATitleIsSkipped() {
    IngestJobPostsRestResponse response = service.ingest(List.of(request().title("  ").build()));

    assertThat(response.getSkipped(), is(1));
    assertThat(response.getJobs(), is(0));
    verify(jobPostRepository, never()).save(any(JobPostEntity.class));
  }

  @Test
  void aListingWithoutACompanyStillCreatesTheJob() {
    when(jobPostRepository.save(any(JobPostEntity.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    ArgumentCaptor<JobPostEntity> captor = ArgumentCaptor.forClass(JobPostEntity.class);

    IngestJobPostsRestResponse response = service.ingest(List.of(request().companyName(null).build()));

    assertThat(response.getJobs(), is(1));
    assertThat(response.getCompanies(), is(0));
    verify(jobPostRepository).save(captor.capture());
    assertThat(captor.getValue().getCompany(), is(nullValue()));
  }

  @Test
  void anOverlongTitleIsTruncatedToTheColumnWidth() {
    savesWhatItIsGiven();
    ArgumentCaptor<JobPostEntity> captor = ArgumentCaptor.forClass(JobPostEntity.class);

    service.ingest(List.of(request().title("x".repeat(400)).build()));

    verify(jobPostRepository).save(captor.capture());
    assertThat(captor.getValue().getTitle().length(), is(255));
  }

  @Test
  void aBatchIngestsEveryListing() {
    savesWhatItIsGiven();

    IngestJobPostsRestResponse response = service.ingest(List.of(
        request().build(),
        request().externalId("999").companyName("Other").build()));

    assertThat(response.getJobs(), is(2));
    assertThat(response.getCompanies(), is(2));
  }
}
