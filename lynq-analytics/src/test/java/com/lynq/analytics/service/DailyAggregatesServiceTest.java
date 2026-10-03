package com.lynq.analytics.service;

import com.lynq.analytics.config.MarketProperties;
import com.lynq.analytics.enums.JobStatus;
import com.lynq.analytics.model.CategoryDailySalaryEntity;
import com.lynq.analytics.model.JobDailyStatsEntity;
import com.lynq.analytics.model.JobPostEntity;
import com.lynq.analytics.model.SkillDailyDemandEntity;
import com.lynq.analytics.repository.JobPostRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DailyAggregatesServiceTest {

  private static final LocalDate SNAPSHOT_ON = LocalDate.parse("2026-10-03");

  @Mock
  private JobPostRepository jobPostRepository;

  @Mock
  private DailyAggregatesStore dailyAggregatesStore;

  private DailyAggregatesService dailyAggregatesService;

  @BeforeEach
  void setUp() {
    dailyAggregatesService = new DailyAggregatesService(jobPostRepository, dailyAggregatesStore,
        new MarketProperties(10, 12, 5, List.of("ARS", "USD")));
  }

  @Test
  void countsTheOpenJobPostsAndThoseWithASalaryPerCategory() {
    givenOpenJobPosts(
        jobPost("TECNOLOGIA", "REMOTE", 100, 200, "ARS", Set.of()),
        jobPost("Tecnología", "REMOTE", null, null, null, Set.of()),
        jobPost("TECNOLOGIA", "REMOTE", null, null, null, Set.of()),
        jobPost(null, "ONSITE", 300, null, "ARS", Set.of()));

    Aggregates aggregates = snapshot();

    assertThat(aggregates.jobStats().stream().map(JobDailyStatsEntity::getCategory).toList(),
        contains("", "TECNOLOGIA"));
    assertThat(aggregates.jobStats().get(0).getOpenJobPosts(), is(1));
    assertThat(aggregates.jobStats().get(0).getOpenWithSalary(), is(1));
    assertThat(aggregates.jobStats().get(1).getOpenJobPosts(), is(3));
    assertThat(aggregates.jobStats().get(1).getOpenWithSalary(), is(1));
    assertThat(aggregates.jobStats().get(1).getSnapshotOn(), is(SNAPSHOT_ON));
  }

  @Test
  void countsTheOpenJobPostsAskingForEachSkillUnderItsMostFrequentSpelling() {
    givenOpenJobPosts(
        jobPost("TECNOLOGIA", "REMOTE", null, null, null, Set.of("Java", "Kafka")),
        jobPost("TECNOLOGIA", "REMOTE", null, null, null, Set.of("Java")),
        jobPost("TECNOLOGIA", "REMOTE", null, null, null, Set.of("java", "Diseño")),
        jobPost("TECNOLOGIA", "REMOTE", null, null, null, Set.of("Diseno")));

    Aggregates aggregates = snapshot();

    assertThat(aggregates.skillDemand().stream().map(SkillDailyDemandEntity::getSkill).toList(),
        contains("Diseno", "Java", "Kafka"));
    assertThat(aggregates.skillDemand().stream().map(SkillDailyDemandEntity::getOpenJobPosts)
        .toList(), contains(2, 3, 1));
  }

  @Test
  void summarisesTheSalariesPerCategoryWorkTypeAndCurrency() {
    List<JobPostEntity> jobPosts = new ArrayList<>();
    for (int salary = 100; salary <= 500; salary += 100) {
      jobPosts.add(jobPost("TECNOLOGIA", "REMOTE", salary, salary, "ARS", Set.of()));
    }
    jobPosts.add(jobPost("TECNOLOGIA", "REMOTE", 3000, 4000, "USD", Set.of()));
    jobPosts.add(jobPost("TECNOLOGIA", "ONSITE", 900, 1100, "ARS", Set.of()));
    givenOpenJobPosts(jobPosts.toArray(JobPostEntity[]::new));

    Aggregates aggregates = snapshot();

    CategoryDailySalaryEntity remoteArs = salary(aggregates, "REMOTE", "ARS");
    assertThat(remoteArs.getN(), is(5));
    assertThat(remoteArs.getMedian(), is(300.0));
    assertThat(remoteArs.getP25(), is(200.0));
    assertThat(remoteArs.getP75(), is(400.0));
    CategoryDailySalaryEntity onsiteArs = salary(aggregates, "ONSITE", "ARS");
    assertThat(onsiteArs.getN(), is(1));
    assertThat(onsiteArs.getMedian(), is(nullValue()));
    assertThat(salary(aggregates, "REMOTE", "USD").getN(), is(1));
  }

  @Test
  void writesEmptyAggregatesWhenNothingIsOpen() {
    givenOpenJobPosts();

    Aggregates aggregates = snapshot();

    assertThat(aggregates.jobStats().isEmpty(), is(true));
    assertThat(aggregates.skillDemand().isEmpty(), is(true));
    assertThat(aggregates.salaries().isEmpty(), is(true));
  }

  private void givenOpenJobPosts(JobPostEntity... jobPosts) {
    when(jobPostRepository.findWithProfileByStatus(JobStatus.OPEN)).thenReturn(List.of(jobPosts));
  }

  @SuppressWarnings("unchecked")
  private Aggregates snapshot() {
    ArgumentCaptor<List<JobDailyStatsEntity>> jobStats = ArgumentCaptor.forClass(List.class);
    ArgumentCaptor<List<SkillDailyDemandEntity>> skillDemand = ArgumentCaptor.forClass(List.class);
    ArgumentCaptor<List<CategoryDailySalaryEntity>> salaries = ArgumentCaptor.forClass(List.class);
    dailyAggregatesService.snapshot(SNAPSHOT_ON);
    verify(dailyAggregatesStore).replace(eq(SNAPSHOT_ON), jobStats.capture(),
        skillDemand.capture(), salaries.capture());
    return new Aggregates(jobStats.getValue(), skillDemand.getValue(), salaries.getValue());
  }

  private static CategoryDailySalaryEntity salary(Aggregates aggregates, String workType,
      String currency) {
    return aggregates.salaries().stream()
        .filter(row -> row.getWorkType().equals(workType) && row.getCurrency().equals(currency))
        .findFirst()
        .orElseThrow();
  }

  private static JobPostEntity jobPost(String category, String workType, Integer down, Integer top,
      String currency, Set<String> skills) {
    String id = UUID.randomUUID().toString();
    return JobPostEntity.builder()
        .id(id)
        .title(id)
        .category(category)
        .workType(workType)
        .source("COMPUTRABAJO")
        .status(JobStatus.OPEN)
        .publishedOn(SNAPSHOT_ON)
        .salaryRangeDown(down)
        .salaryRangeTop(top)
        .salaryCurrency(currency)
        .skills(new HashSet<>(skills))
        .build();
  }

  private record Aggregates(List<JobDailyStatsEntity> jobStats,
      List<SkillDailyDemandEntity> skillDemand, List<CategoryDailySalaryEntity> salaries) {
  }
}
