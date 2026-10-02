package com.lynq.analytics.service;

import com.lynq.analytics.cache.AnalyticsCaches;
import com.lynq.analytics.config.StandingProperties;
import com.lynq.analytics.exceptions.ForbiddenException;
import com.lynq.analytics.exceptions.NotFoundException;
import com.lynq.analytics.model.ApplicationEntity;
import com.lynq.analytics.repository.ApplicationRepository;
import com.lynq.analytics.repository.JobPostRepository;
import com.lynq.analytics.stats.Standing;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StandingService {

  private static final String NOT_APPLIED =
      "Only a candidate who applied to the job post can read their standing";

  private final ApplicationRepository applicationRepository;
  private final JobPostRepository jobPostRepository;
  private final StandingProperties properties;

  public StandingService(ApplicationRepository applicationRepository,
      JobPostRepository jobPostRepository, StandingProperties properties) {
    this.applicationRepository = applicationRepository;
    this.jobPostRepository = jobPostRepository;
    this.properties = properties;
  }

  @Cacheable(cacheNames = AnalyticsCaches.STANDING, key = "#jobId + ':' + #candidateId")
  @Transactional(readOnly = true)
  public Standing standing(String jobId, String candidateId) {
    ApplicationEntity application = applicationRepository
        .findByJobIdAndCandidateId(jobId, candidateId)
        .orElseThrow(() -> jobPostRepository.existsById(jobId)
            ? new ForbiddenException(NOT_APPLIED)
            : new NotFoundException("Job post '" + jobId + "' not found"));
    return Standing.of(application.getLynqScore(), applicationRepository.findScoresByJobId(jobId),
        properties.minApplicants());
  }
}
