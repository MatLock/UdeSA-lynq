package com.lynq.analytics.service;

import com.lynq.analytics.cache.AnalyticsCaches;
import com.lynq.analytics.config.SalaryProperties;
import com.lynq.analytics.exceptions.NotFoundException;
import com.lynq.analytics.model.CandidateEntity;
import com.lynq.analytics.model.JobPostEntity;
import com.lynq.analytics.repository.JobPostRepository;
import com.lynq.analytics.stats.SalaryDistribution;
import com.lynq.analytics.stats.SalaryInsights;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SalaryService {

  private final JobPostRepository jobPostRepository;
  private final SimilarityService similarityService;
  private final SalaryProperties properties;

  public SalaryService(JobPostRepository jobPostRepository, SimilarityService similarityService,
      SalaryProperties properties) {
    this.jobPostRepository = jobPostRepository;
    this.similarityService = similarityService;
    this.properties = properties;
  }

  @Cacheable(cacheNames = AnalyticsCaches.SALARY, key = "#jobId")
  @Transactional(readOnly = true)
  public SalaryInsights salary(String jobId) {
    JobPostEntity reference = jobPostRepository.findById(jobId)
        .orElseThrow(() -> new NotFoundException("Job post '" + jobId + "' not found"));
    String currency = reference.getSalaryCurrency() != null
        ? reference.getSalaryCurrency()
        : properties.defaultCurrency();

    List<Double> positionSalaries = similarityService
        .findSimilarJobPosts(jobId, post -> currency.equals(post.getSalaryCurrency())
            && salaryOf(post) != null)
        .items().stream()
        .map(SalaryService::salaryOf)
        .toList();
    List<Integer> peerSalaries = similarityService
        .findSimilarCandidates(jobId, candidate -> candidate.getExpectedSalary() != null
            && currency.equals(candidate.getExpectedSalaryCurrency()))
        .items().stream()
        .map(CandidateEntity::getExpectedSalary)
        .toList();

    return new SalaryInsights(
        SalaryDistribution.of(positionSalaries, currency, properties.minSample()),
        SalaryDistribution.of(peerSalaries, currency, properties.minSample()));
  }

  static Double salaryOf(JobPostEntity post) {
    List<Integer> bounds = Stream.of(post.getSalaryRangeDown(), post.getSalaryRangeTop())
        .filter(Objects::nonNull)
        .toList();
    if (bounds.isEmpty()) {
      return null;
    }
    return bounds.stream().mapToInt(Integer::intValue).average().orElseThrow();
  }
}
