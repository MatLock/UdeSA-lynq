package com.lynq.backend.service;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.impl.NameBasedGenerator;
import com.lynq.backend.controller.request.IngestJobPostRequest;
import com.lynq.backend.controller.response.IngestJobPostsRestResponse;
import com.lynq.backend.enums.JobStatus;
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
  private static final int MAX_TAG_LENGTH = 255;

  private final JobPostRepository jobPostRepository;
  private final CompanyRepository companyRepository;
  private final JobPostSkillRepository jobPostSkillRepository;
  private final JobPostSimilarityTagRepository jobPostSimilarityTagRepository;

  public JobIngestService(JobPostRepository jobPostRepository, CompanyRepository companyRepository,
      JobPostSkillRepository jobPostSkillRepository,
      JobPostSimilarityTagRepository jobPostSimilarityTagRepository) {
    this.jobPostRepository = jobPostRepository;
    this.companyRepository = companyRepository;
    this.jobPostSkillRepository = jobPostSkillRepository;
    this.jobPostSimilarityTagRepository = jobPostSimilarityTagRepository;
  }

  @Transactional
  public IngestJobPostsRestResponse ingest(List<IngestJobPostRequest> jobPosts) {
    int jobs = 0;
    int companies = 0;
    int skills = 0;
    int similarityTags = 0;
    int skipped = 0;

    for (IngestJobPostRequest request : jobPosts) {
      String title = truncate(request.getTitle(), MAX_TITLE_LENGTH);
      if (title == null || title.isBlank()) {
        skipped++;
        continue;
      }

      CompanyResult company = upsertCompany(request.getCompanyName());
      if (company.created()) {
        companies++;
      }

      JobPostEntity job = upsertJob(request, title, company.entity());
      jobs++;
      skills += replaceSkills(job, request.getSkills());
      similarityTags += replaceSimilarityTags(job, request.getSimilarityTags());
    }

    return IngestJobPostsRestResponse.builder()
        .jobs(jobs)
        .companies(companies)
        .skills(skills)
        .similarityTags(similarityTags)
        .skipped(skipped)
        .build();
  }

  private CompanyResult upsertCompany(String rawName) {
    String name = truncate(rawName, MAX_COMPANY_NAME_LENGTH);
    if (name == null || name.isBlank()) {
      return new CompanyResult(null, false);
    }

    String id = deterministicId(COMPANY_KEY_PREFIX + name.toLowerCase(Locale.ROOT));
    Optional<CompanyEntity> existing = companyRepository.findById(id);
    if (existing.isPresent()) {
      return new CompanyResult(existing.get(), false);
    }

    CompanyEntity company = CompanyEntity.builder()
        .id(id)
        .name(name)
        .createdOn(LocalDate.now(ZoneOffset.UTC))
        .build();
    return new CompanyResult(companyRepository.save(company), true);
  }

  private JobPostEntity upsertJob(IngestJobPostRequest request, String title, CompanyEntity company) {
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
    job.setJobUrl(truncate(request.getJobUrl(), MAX_JOB_URL_LENGTH));
    job.setJobPostSource(request.getJobPostSource());
    job.setCompany(company);
    job.setCreatedOn(postedOn(request.getPostedAt()));

    return jobPostRepository.save(job);
  }

  private int replaceSkills(JobPostEntity job, List<String> requested) {
    jobPostSkillRepository.deleteAll(jobPostSkillRepository.findByJobPost(job));

    List<JobPostSkillEntity> entities = new ArrayList<>();
    for (String skill : normalize(requested)) {
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

  private int replaceSimilarityTags(JobPostEntity job, List<String> requested) {
    jobPostSimilarityTagRepository.deleteAll(jobPostSimilarityTagRepository.findByJobPost(job));

    List<JobPostSimilarityTagEntity> entities = new ArrayList<>();
    for (String tag : normalize(requested)) {
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
}
