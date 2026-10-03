package com.lynq.analytics.service;

import com.lynq.analytics.cache.AnalyticsCaches;
import com.lynq.analytics.config.MarketProperties;
import com.lynq.analytics.exceptions.BadRequestException;
import com.lynq.analytics.model.CategoryDailySalaryEntity;
import com.lynq.analytics.model.JobDailyStatsEntity;
import com.lynq.analytics.model.SkillDailyDemandEntity;
import com.lynq.analytics.repository.CategoryDailySalaryRepository;
import com.lynq.analytics.repository.JobDailyStatsRepository;
import com.lynq.analytics.repository.JobPostRepository;
import com.lynq.analytics.repository.JobPostRepository.PublishedCount;
import com.lynq.analytics.repository.SkillDailyDemandRepository;
import com.lynq.analytics.stats.Folding;
import com.lynq.analytics.stats.Market;
import com.lynq.analytics.stats.Market.CategorySalary;
import com.lynq.analytics.stats.Market.MarketSalary;
import com.lynq.analytics.stats.Market.SkillDemand;
import com.lynq.analytics.stats.Market.WeeklyCount;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MarketService {

  private final JobDailyStatsRepository jobDailyStatsRepository;
  private final SkillDailyDemandRepository skillDailyDemandRepository;
  private final CategoryDailySalaryRepository categoryDailySalaryRepository;
  private final JobPostRepository jobPostRepository;
  private final MarketProperties properties;
  private final Clock clock;

  public MarketService(JobDailyStatsRepository jobDailyStatsRepository,
      SkillDailyDemandRepository skillDailyDemandRepository,
      CategoryDailySalaryRepository categoryDailySalaryRepository,
      JobPostRepository jobPostRepository, MarketProperties properties, Clock clock) {
    this.jobDailyStatsRepository = jobDailyStatsRepository;
    this.skillDailyDemandRepository = skillDailyDemandRepository;
    this.categoryDailySalaryRepository = categoryDailySalaryRepository;
    this.jobPostRepository = jobPostRepository;
    this.properties = properties;
    this.clock = clock;
  }

  @Cacheable(cacheNames = AnalyticsCaches.MARKET, key = "#currency")
  @Transactional(readOnly = true)
  public Market market(String currency) {
    if (!properties.currencies().contains(currency)) {
      throw new BadRequestException("Unsupported currency '" + currency + "', expected one of "
          + properties.currencies());
    }
    List<WeeklyCount> publishedPerWeek = publishedPerWeek(LocalDate.now(clock));
    return jobDailyStatsRepository.findLatestSnapshotOn()
        .map(snapshotOn -> snapshotMarket(snapshotOn, currency, publishedPerWeek))
        .orElseGet(() -> new Market(null, 0, 0, List.of(), new MarketSalary(currency, List.of()),
            publishedPerWeek));
  }

  private Market snapshotMarket(LocalDate snapshotOn, String currency,
      List<WeeklyCount> publishedPerWeek) {
    List<JobDailyStatsEntity> jobStats = jobDailyStatsRepository.findBySnapshotOn(snapshotOn);
    return new Market(
        snapshotOn,
        jobStats.stream().mapToInt(JobDailyStatsEntity::getOpenJobPosts).sum(),
        jobStats.stream().mapToInt(JobDailyStatsEntity::getOpenWithSalary).sum(),
        skillDemand(snapshotOn),
        new MarketSalary(currency, categoryDailySalaryRepository
            .findBySnapshotOnAndCurrencyOrderByCategoryAscWorkTypeAsc(snapshotOn, currency)
            .stream()
            .map(MarketService::categorySalaryOf)
            .toList()),
        publishedPerWeek);
  }

  private List<SkillDemand> skillDemand(LocalDate snapshotOn) {
    List<SkillDailyDemandEntity> mostDemanded = skillDailyDemandRepository
        .findMostDemanded(snapshotOn, PageRequest.of(0, properties.topSkills()));
    LocalDate weekBefore = snapshotOn.minusWeeks(1);
    if (mostDemanded.isEmpty() || !jobDailyStatsRepository.existsBySnapshotOn(weekBefore)) {
      return mostDemanded.stream()
          .map(skill -> new SkillDemand(skill.getSkill(), skill.getOpenJobPosts(), null))
          .toList();
    }
    Map<String, Integer> weekBeforeDemand = new HashMap<>();
    skillDailyDemandRepository.findBySnapshotOnAndSkillIn(weekBefore,
            mostDemanded.stream().map(SkillDailyDemandEntity::getSkill).toList())
        .forEach(skill -> weekBeforeDemand.put(Folding.fold(skill.getSkill()),
            skill.getOpenJobPosts()));
    return mostDemanded.stream()
        .map(skill -> new SkillDemand(skill.getSkill(), skill.getOpenJobPosts(),
            skill.getOpenJobPosts()
                - weekBeforeDemand.getOrDefault(Folding.fold(skill.getSkill()), 0)))
        .toList();
  }

  private List<WeeklyCount> publishedPerWeek(LocalDate today) {
    LocalDate currentWeek = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    LocalDate firstWeek = currentWeek.minusWeeks(properties.weeks());
    Map<LocalDate, Long> publishedByWeek = jobPostRepository
        .countPublishedBetween(firstWeek, currentWeek.minusDays(1)).stream()
        .collect(Collectors.groupingBy(
            count -> count.getPublishedOn().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
            Collectors.summingLong(PublishedCount::getJobPosts)));
    return IntStream.range(0, properties.weeks())
        .mapToObj(firstWeek::plusWeeks)
        .map(weekStart -> new WeeklyCount(weekStart,
            publishedByWeek.getOrDefault(weekStart, 0L).intValue()))
        .toList();
  }

  private static CategorySalary categorySalaryOf(CategoryDailySalaryEntity row) {
    return new CategorySalary(
        DailyAggregatesService.UNCATEGORIZED.equals(row.getCategory()) ? null : row.getCategory(),
        row.getWorkType(), row.getN(), row.getMedian(), row.getP25(), row.getP75(),
        row.getMedian() == null);
  }
}
