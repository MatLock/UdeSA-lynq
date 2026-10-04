package com.lynq.backend.service;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.impl.NameBasedGenerator;
import com.lynq.backend.controller.request.IngestJobPostRequest;
import com.lynq.backend.controller.response.IngestJobPostsRestResponse;
import com.lynq.backend.enums.JobStatus;
import com.lynq.backend.event.DomainEventPublisher;
import com.lynq.backend.event.DomainEvents;
import com.lynq.backend.model.CompanyEntity;
import com.lynq.backend.model.JobPostEntity;
import com.lynq.backend.model.JobPostSimilarityTagEntity;
import com.lynq.backend.model.JobPostSkillEntity;
import com.lynq.backend.repository.CompanyRepository;
import com.lynq.backend.repository.JobPostRepository;
import com.lynq.backend.repository.JobPostSimilarityTagRepository;
import com.lynq.backend.repository.JobPostSkillRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JobIngestService {

  private static final UUID FEEDER_NAMESPACE =
      UUID.fromString("274b7337-c645-50e5-9f69-dbd36c3428a2");

  private static final String COMPANY_KEY_PREFIX = "company|";
  private static final String KEY_SEPARATOR = "|";
  private static final int MAX_COMPANY_NAME_LENGTH = 255;
  private static final int MAX_TITLE_LENGTH = 255;
  private static final int MAX_JOB_URL_LENGTH = 2048;
  private static final int MAX_LOGO_URL_LENGTH = 2048;
  private static final int MAX_TAG_LENGTH = 255;
  private static final int MAX_CATEGORY_LENGTH = 64;

  private final JobPostRepository jobPostRepository;
  private final CompanyRepository companyRepository;
  private final JobPostSkillRepository jobPostSkillRepository;
  private final JobPostSimilarityTagRepository jobPostSimilarityTagRepository;
  private final DomainEventPublisher domainEventPublisher;

  public JobIngestService(JobPostRepository jobPostRepository, CompanyRepository companyRepository,
      JobPostSkillRepository jobPostSkillRepository,
      JobPostSimilarityTagRepository jobPostSimilarityTagRepository,
      DomainEventPublisher domainEventPublisher) {
    this.jobPostRepository = jobPostRepository;
    this.companyRepository = companyRepository;
    this.jobPostSkillRepository = jobPostSkillRepository;
    this.jobPostSimilarityTagRepository = jobPostSimilarityTagRepository;
    this.domainEventPublisher = domainEventPublisher;
  }

  @Transactional
  public IngestJobPostsRestResponse ingest(List<IngestJobPostRequest> jobPosts) {
    int jobs = 0;
    int companies = 0;
    int skills = 0;
    int similarityTags = 0;
    int skipped = 0;
    int reopened = 0;
    Instant ingestedOn = Instant.now();
    LocalDate seenOn = LocalDate.now(ZoneOffset.UTC);

    for (IngestJobPostRequest request : jobPosts) {
      String title = truncate(request.getTitle(), MAX_TITLE_LENGTH);
      if (title == null || title.isBlank()) {
        skipped++;
        continue;
      }

      CompanyResult company = upsertCompany(request);
      if (company.created()) {
        companies++;
      }

      UpsertedJob upserted = upsertJob(request, title, company.entity(), seenOn);
      JobPostEntity job = upserted.entity();
      jobs++;
      Set<String> jobSkills = normalize(request.getSkills());
      Set<String> jobSimilarityTags = normalize(request.getSimilarityTags());
      skills += replaceSkills(job, jobSkills);
      similarityTags += replaceSimilarityTags(job, jobSimilarityTags);
      publishIngested(job, jobSkills, jobSimilarityTags, ingestedOn);
      if (upserted.reopened()) {
        reopened++;
        domainEventPublisher.publish(DomainEvents.jobPostReopened(job, seenOn, ingestedOn));
      }
    }

    return IngestJobPostsRestResponse.builder()
        .jobs(jobs)
        .companies(companies)
        .skills(skills)
        .similarityTags(similarityTags)
        .skipped(skipped)
        .reopened(reopened)
        .build();
  }

  private CompanyResult upsertCompany(IngestJobPostRequest request) {
    String name = truncate(request.getCompanyName(), MAX_COMPANY_NAME_LENGTH);
    if (name == null || name.isBlank()) {
      return new CompanyResult(null, false);
    }

    String logoUrl = truncate(request.getCompanyLogoUrl(), MAX_LOGO_URL_LENGTH);
    String id = deterministicId(COMPANY_KEY_PREFIX + name.toLowerCase(Locale.ROOT));
    Optional<CompanyEntity> existing = companyRepository.findById(id);
    if (existing.isPresent()) {
      return new CompanyResult(refreshLogo(existing.get(), logoUrl), false);
    }

    CompanyEntity company = CompanyEntity.builder()
        .id(id)
        .name(name)
        .logoUrl(logoUrl)
        .createdOn(LocalDate.now(ZoneOffset.UTC))
        .build();
    return new CompanyResult(companyRepository.save(company), true);
  }

  private CompanyEntity refreshLogo(CompanyEntity company, String logoUrl) {
    if (logoUrl == null || logoUrl.equals(company.getLogoUrl())) {
      return company;
    }
    company.setLogoUrl(logoUrl);
    return companyRepository.save(company);
  }

  private UpsertedJob upsertJob(IngestJobPostRequest request, String title, CompanyEntity company,
      LocalDate seenOn) {
    String source = request.getJobPostSource().name().toLowerCase(Locale.ROOT);
    String id = deterministicId(source + KEY_SEPARATOR + request.getExternalId());

    JobPostEntity job = jobPostRepository.findById(id).orElseGet(() -> JobPostEntity.builder()
        .id(id)
        .totalSeen(0L)
        .jobStatus(JobStatus.OPEN)
        .build());

    job.setTitle(title);
    job.setDescription(request.getDescription());
    job.setWorkType(request.getWorkType());
    job.setSalaryRangeDown(request.getSalaryRangeDown());
    job.setSalaryRangeTop(request.getSalaryRangeTop());
    job.setSalaryCurrency(JobService.currencyOf(request.getSalaryRangeDown(),
        request.getSalaryRangeTop(), request.getSalaryCurrency()));
    refreshCategory(job, request.getCategory());
    job.setJobUrl(truncate(request.getJobUrl(), MAX_JOB_URL_LENGTH));
    job.setJobPostSource(request.getJobPostSource());
    job.setCompany(company);
    job.setCreatedOn(postedOn(request.getPostedAt()));
    job.setLastSeenOn(seenOn);
    boolean reopened = reopenIfClosed(job);

    return new UpsertedJob(jobPostRepository.save(job), reopened);
  }

  private void refreshCategory(JobPostEntity job, String category) {
    String trimmed = category == null ? null : truncate(category.trim(), MAX_CATEGORY_LENGTH);
    if (trimmed == null || trimmed.isEmpty()) {
      return;
    }
    job.setCategory(trimmed);
  }

  private boolean reopenIfClosed(JobPostEntity job) {
    if (job.getJobStatus() != JobStatus.CLOSE) {
      return false;
    }
    job.setJobStatus(JobStatus.OPEN);
    job.setClosedOn(null);
    job.setCloseReason(null);
    return true;
  }

  private void publishIngested(JobPostEntity job, Set<String> skills, Set<String> similarityTags,
      Instant ingestedOn) {
    domainEventPublisher.publish(
        DomainEvents.jobPostPublished(job, skills, similarityTags, ingestedOn));
  }

  private int replaceSkills(JobPostEntity job, Set<String> skills) {
    jobPostSkillRepository.deleteAll(jobPostSkillRepository.findByJobPost(job));

    List<JobPostSkillEntity> entities = new ArrayList<>();
    for (String skill : skills) {
      entities.add(JobPostSkillEntity.builder()
          .id(deterministicId("skill" + KEY_SEPARATOR + job.getId() + KEY_SEPARATOR
              + skill.toLowerCase(Locale.ROOT)))
          .jobPost(job)
          .skill(skill)
          .build());
    }
    jobPostSkillRepository.saveAll(entities);
    return entities.size();
  }

  private int replaceSimilarityTags(JobPostEntity job, Set<String> similarityTags) {
    jobPostSimilarityTagRepository.deleteAll(jobPostSimilarityTagRepository.findByJobPost(job));

    List<JobPostSimilarityTagEntity> entities = new ArrayList<>();
    for (String tag : similarityTags) {
      entities.add(JobPostSimilarityTagEntity.builder()
          .id(deterministicId("tag" + KEY_SEPARATOR + job.getId() + KEY_SEPARATOR
              + tag.toLowerCase(Locale.ROOT)))
          .jobPost(job)
          .similarityTag(tag)
          .build());
    }
    jobPostSimilarityTagRepository.saveAll(entities);
    return entities.size();
  }

  private Set<String> normalize(List<String> values) {
    Set<String> unique = new LinkedHashSet<>();
    if (values == null) {
      return unique;
    }
    Set<String> seenLowercase = new LinkedHashSet<>();
    for (String value : values) {
      if (value == null) {
        continue;
      }
      String trimmed = truncate(value.trim(), MAX_TAG_LENGTH);
      if (trimmed.isEmpty() || !seenLowercase.add(trimmed.toLowerCase(Locale.ROOT))) {
        continue;
      }
      unique.add(trimmed);
    }
    return unique;
  }

  private LocalDate postedOn(Long postedAt) {
    if (postedAt == null || postedAt <= 0) {
      return LocalDate.now(ZoneOffset.UTC);
    }
    return Instant.ofEpochMilli(postedAt).atZone(ZoneOffset.UTC).toLocalDate();
  }

  private String truncate(String value, int length) {
    if (value == null) {
      return null;
    }
    return value.length() > length ? value.substring(0, length) : value;
  }

  private String deterministicId(String key) {
    NameBasedGenerator generator = Generators.nameBasedGenerator(FEEDER_NAMESPACE);
    return generator.generate(key).toString();
  }

  private record CompanyResult(CompanyEntity entity, boolean created) {
  }

  private record UpsertedJob(JobPostEntity entity, boolean reopened) {
  }
}
