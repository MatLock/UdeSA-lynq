package com.lynq.analytics.service;

import com.lynq.analytics.cache.AnalyticsCaches;
import com.lynq.analytics.config.MarketProperties;
import com.lynq.analytics.enums.JobStatus;
import com.lynq.analytics.model.CategoryDailySalaryEntity;
import com.lynq.analytics.model.JobDailyStatsEntity;
import com.lynq.analytics.model.JobPostEntity;
import com.lynq.analytics.model.SkillDailyDemandEntity;
import com.lynq.analytics.repository.JobPostRepository;
import com.lynq.analytics.stats.Distribution;
import com.lynq.analytics.stats.Folding;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import lombok.extern.log4j.Log4j2;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;

@Service
@Log4j2
public class DailyAggregatesService {

  public static final String UNCATEGORIZED = "";

  private final JobPostRepository jobPostRepository;
  private final DailyAggregatesStore dailyAggregatesStore;
  private final MarketProperties properties;

  public DailyAggregatesService(JobPostRepository jobPostRepository,
      DailyAggregatesStore dailyAggregatesStore, MarketProperties properties) {
    this.jobPostRepository = jobPostRepository;
    this.dailyAggregatesStore = dailyAggregatesStore;
    this.properties = properties;
  }

  @CacheEvict(cacheNames = AnalyticsCaches.MARKET, allEntries = true)
  public void snapshot(LocalDate snapshotOn) {
    List<JobPostEntity> openJobPosts = jobPostRepository.findWithProfileByStatus(JobStatus.OPEN);
    Map<String, String> categories = spellingsByKey(openJobPosts.stream()
        .map(JobPostEntity::getCategory)
        .filter(Objects::nonNull)
        .filter(category -> !category.isBlank())
        .map(String::trim)
        .toList());

    List<JobDailyStatsEntity> jobStats = jobStats(snapshotOn, openJobPosts, categories);
    List<SkillDailyDemandEntity> skillDemand = skillDemand(snapshotOn, openJobPosts);
    List<CategoryDailySalaryEntity> salaries = salaries(snapshotOn, openJobPosts, categories);
    dailyAggregatesStore.replace(snapshotOn, jobStats, skillDemand, salaries);
    log.info("message= Wrote the daily aggregates, snapshotOn={}, openJobPosts={}, skills={}, "
        + "salaryGroups={}", snapshotOn, openJobPosts.size(), skillDemand.size(), salaries.size());
  }

  private static List<JobDailyStatsEntity> jobStats(LocalDate snapshotOn,
      List<JobPostEntity> openJobPosts, Map<String, String> categories) {
    Map<String, List<JobPostEntity>> byCategory = openJobPosts.stream()
        .collect(Collectors.groupingBy(jobPost -> categoryOf(jobPost, categories), TreeMap::new,
            Collectors.toList()));
    return byCategory.entrySet().stream()
        .map(entry -> JobDailyStatsEntity.builder()
            .snapshotOn(snapshotOn)
            .category(entry.getKey())
            .openJobPosts(entry.getValue().size())
            .openWithSalary((int) entry.getValue().stream()
                .filter(jobPost -> SalaryService.salaryOf(jobPost) != null)
                .count())
            .build())
        .toList();
  }

  private static List<SkillDailyDemandEntity> skillDemand(LocalDate snapshotOn,
      List<JobPostEntity> openJobPosts) {
    Map<String, Set<JobPostEntity>> jobPostsBySkill = new TreeMap<>();
    Map<String, List<String>> spellingsBySkill = new HashMap<>();
    openJobPosts.forEach(jobPost -> jobPost.getSkills().stream()
        .filter(Objects::nonNull)
        .filter(skill -> !skill.isBlank())
        .forEach(skill -> {
          String key = Folding.fold(skill);
          jobPostsBySkill.computeIfAbsent(key, ignored -> new LinkedHashSet<>()).add(jobPost);
          spellingsBySkill.computeIfAbsent(key, ignored -> new ArrayList<>()).add(skill.trim());
        }));
    return jobPostsBySkill.entrySet().stream()
        .map(entry -> SkillDailyDemandEntity.builder()
            .snapshotOn(snapshotOn)
            .skill(Folding.mostFrequent(spellingsBySkill.get(entry.getKey())))
            .openJobPosts(entry.getValue().size())
            .build())
        .toList();
  }

  private List<CategoryDailySalaryEntity> salaries(LocalDate snapshotOn,
      List<JobPostEntity> openJobPosts, Map<String, String> categories) {
    Map<SalaryGroup, List<Double>> salariesByGroup = openJobPosts.stream()
        .filter(jobPost -> jobPost.getSalaryCurrency() != null)
        .filter(jobPost -> SalaryService.salaryOf(jobPost) != null)
        .collect(Collectors.groupingBy(
            jobPost -> new SalaryGroup(categoryOf(jobPost, categories), jobPost.getWorkType(),
                jobPost.getSalaryCurrency()),
            Collectors.mapping(SalaryService::salaryOf, Collectors.toList())));
    return salariesByGroup.entrySet().stream()
        .map(entry -> salaryRow(snapshotOn, entry.getKey(), entry.getValue()))
        .toList();
  }

  private CategoryDailySalaryEntity salaryRow(LocalDate snapshotOn, SalaryGroup group,
      List<Double> salaries) {
    Distribution distribution = Distribution.of(salaries);
    boolean enough = distribution.n() >= properties.minSample();
    return CategoryDailySalaryEntity.builder()
        .snapshotOn(snapshotOn)
        .category(group.category())
        .workType(group.workType())
        .currency(group.currency())
        .n(distribution.n())
        .median(enough ? distribution.median() : null)
        .p25(enough ? distribution.p25() : null)
        .p75(enough ? distribution.p75() : null)
        .build();
  }

  private static String categoryOf(JobPostEntity jobPost, Map<String, String> categories) {
    String category = jobPost.getCategory();
    if (category == null || category.isBlank()) {
      return UNCATEGORIZED;
    }
    return categories.get(Folding.fold(category));
  }

  private static Map<String, String> spellingsByKey(List<String> values) {
    return values.stream().collect(Collectors.groupingBy(Folding::fold,
        Collectors.collectingAndThen(Collectors.toList(), Folding::mostFrequent)));
  }

  private record SalaryGroup(String category, String workType, String currency) {
  }
}
