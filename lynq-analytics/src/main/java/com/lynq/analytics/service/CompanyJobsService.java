package com.lynq.analytics.service;

import com.lynq.analytics.cache.AnalyticsCaches;
import com.lynq.analytics.config.StandingProperties;
import com.lynq.analytics.model.JobPostEntity;
import com.lynq.analytics.repository.ApplicationRepository;
import com.lynq.analytics.repository.ApplicationRepository.JobScore;
import com.lynq.analytics.repository.JobPostRepository;
import com.lynq.analytics.stats.CompanyJobs;
import com.lynq.analytics.stats.CompanyJobs.CompanyJob;
import com.lynq.analytics.stats.Distribution;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CompanyJobsService {

  private final JobPostRepository jobPostRepository;
  private final ApplicationRepository applicationRepository;
  private final StandingProperties properties;

  public CompanyJobsService(JobPostRepository jobPostRepository,
      ApplicationRepository applicationRepository, StandingProperties properties) {
    this.jobPostRepository = jobPostRepository;
    this.applicationRepository = applicationRepository;
    this.properties = properties;
  }

  @Cacheable(cacheNames = AnalyticsCaches.COMPANY_JOBS, key = "#userId")
  @Transactional(readOnly = true)
  public CompanyJobs jobs(String userId) {
    List<JobPostEntity> jobPosts =
        jobPostRepository.findByCreatedByUserIdOrderByPublishedOnDescIdAsc(userId);
    if (jobPosts.isEmpty()) {
      return new CompanyJobs(List.of());
    }
    Map<String, List<Integer>> scoresByJobPost = applicationRepository
        .findScoresByJobIds(jobPosts.stream().map(JobPostEntity::getId).toList()).stream()
        .collect(Collectors.groupingBy(JobScore::getJobId,
            Collectors.mapping(JobScore::getLynqScore, Collectors.toList())));
    return new CompanyJobs(jobPosts.stream()
        .map(jobPost -> jobOf(jobPost, scoresByJobPost.getOrDefault(jobPost.getId(), List.of())))
        .toList());
  }

  private CompanyJob jobOf(JobPostEntity jobPost, List<Integer> scores) {
    boolean insufficientData = scores.size() < properties.minApplicants();
    return new CompanyJob(jobPost.getId(), jobPost.getTitle(), jobPost.getStatus().name(),
        jobPost.getPublishedOn(), scores.size(),
        insufficientData ? null : Distribution.of(scores).median(), insufficientData);
  }
}
