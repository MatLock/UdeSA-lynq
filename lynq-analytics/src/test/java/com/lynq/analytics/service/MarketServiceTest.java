package com.lynq.analytics.service;

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
import com.lynq.analytics.stats.Market;
import com.lynq.analytics.stats.Market.CategorySalary;
import com.lynq.analytics.stats.Market.SkillDemand;
import com.lynq.analytics.stats.Market.WeeklyCount;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarketServiceTest {

  private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-03T15:00:00Z"),
      ZoneId.of("America/Argentina/Buenos_Aires"));
  private static final LocalDate SNAPSHOT_ON = LocalDate.parse("2026-10-03");
  private static final LocalDate WEEK_BEFORE = LocalDate.parse("2026-09-26");
  private static final LocalDate FIRST_WEEK = LocalDate.parse("2026-09-07");
  private static final LocalDate LAST_SUNDAY = LocalDate.parse("2026-09-27");

  @Mock
  private JobDailyStatsRepository jobDailyStatsRepository;

  @Mock
  private SkillDailyDemandRepository skillDailyDemandRepository;

  @Mock
  private CategoryDailySalaryRepository categoryDailySalaryRepository;

  @Mock
  private JobPostRepository jobPostRepository;

  private MarketService marketService;

  @BeforeEach
  void setUp() {
    marketService = new MarketService(jobDailyStatsRepository, skillDailyDemandRepository,
        categoryDailySalaryRepository, jobPostRepository,
        new MarketProperties(2, 3, 5, List.of("ARS", "USD")), CLOCK);
  }

  @Test
  void countsTheJobPostsPublishedInEachOfTheLastCompleteWeeks() {
    when(jobPostRepository.countPublishedBetween(FIRST_WEEK, LAST_SUNDAY)).thenReturn(List.of(
        published("2026-09-07", 2), published("2026-09-13", 1), published("2026-09-22", 4)));
    when(jobDailyStatsRepository.findLatestSnapshotOn()).thenReturn(Optional.empty());

    Market market = marketService.market("ARS");

    assertThat(market.publishedPerWeek(), contains(
        new WeeklyCount(FIRST_WEEK, 3),
        new WeeklyCount(LocalDate.parse("2026-09-14"), 0),
        new WeeklyCount(LocalDate.parse("2026-09-21"), 4)));
  }

  @Test
  void answersNoSnapshotBeforeTheFirstOne() {
    when(jobPostRepository.countPublishedBetween(any(), any())).thenReturn(List.of());
    when(jobDailyStatsRepository.findLatestSnapshotOn()).thenReturn(Optional.empty());

    Market market = marketService.market("USD");

    assertThat(market.snapshotOn(), is(nullValue()));
    assertThat(market.openJobPosts(), is(0));
    assertThat(market.skillDemand(), is(empty()));
    assertThat(market.salary().currency(), is("USD"));
    assertThat(market.salary().rows(), is(empty()));
  }

  @Test
  void readsTheLatestSnapshotWithTheWeeklyChangeOfTheMostDemandedSkills() {
    givenTheLatestSnapshot();
    when(jobDailyStatsRepository.existsBySnapshotOn(WEEK_BEFORE)).thenReturn(true);
    when(skillDailyDemandRepository.findBySnapshotOnAndSkillIn(WEEK_BEFORE,
        List.of("Java", "SQL"))).thenReturn(List.of(demand(WEEK_BEFORE, "java", 10)));
    when(categoryDailySalaryRepository.findBySnapshotOnAndCurrencyOrderByCategoryAscWorkTypeAsc(
        SNAPSHOT_ON, "ARS")).thenReturn(List.of(
            salary("", "REMOTE", 7, 300.0), salary("TECNOLOGIA", "ONSITE", 2, null)));

    Market market = marketService.market("ARS");

    assertThat(market.snapshotOn(), is(SNAPSHOT_ON));
    assertThat(market.openJobPosts(), is(30));
    assertThat(market.openWithSalary(), is(9));
    assertThat(market.skillDemand(), contains(new SkillDemand("Java", 14, 4),
        new SkillDemand("SQL", 9, 9)));
    List<CategorySalary> rows = market.salary().rows();
    assertThat(rows.get(0).category(), is(nullValue()));
    assertThat(rows.get(0).median(), is(300.0));
    assertThat(rows.get(0).insufficientData(), is(false));
    assertThat(rows.get(1).category(), is("TECNOLOGIA"));
    assertThat(rows.get(1).insufficientData(), is(true));
  }

  @Test
  void leavesTheWeeklyChangeEmptyWithoutASnapshotAWeekEarlier() {
    givenTheLatestSnapshot();
    when(jobDailyStatsRepository.existsBySnapshotOn(WEEK_BEFORE)).thenReturn(false);
    when(categoryDailySalaryRepository.findBySnapshotOnAndCurrencyOrderByCategoryAscWorkTypeAsc(
        SNAPSHOT_ON, "ARS")).thenReturn(List.of());

    Market market = marketService.market("ARS");

    assertThat(market.skillDemand(), contains(new SkillDemand("Java", 14, null),
        new SkillDemand("SQL", 9, null)));
  }

  @Test
  void refusesACurrencyItDoesNotCompare() {
    assertThrows(BadRequestException.class, () -> marketService.market("EUR"));

    verifyNoInteractions(jobDailyStatsRepository, jobPostRepository);
  }

  private void givenTheLatestSnapshot() {
    when(jobPostRepository.countPublishedBetween(any(), any())).thenReturn(List.of());
    when(jobDailyStatsRepository.findLatestSnapshotOn()).thenReturn(Optional.of(SNAPSHOT_ON));
    when(jobDailyStatsRepository.findBySnapshotOn(SNAPSHOT_ON)).thenReturn(List.of(
        new JobDailyStatsEntity(SNAPSHOT_ON, "", 12, 4),
        new JobDailyStatsEntity(SNAPSHOT_ON, "TECNOLOGIA", 18, 5)));
    when(skillDailyDemandRepository.findMostDemanded(SNAPSHOT_ON, PageRequest.of(0, 2)))
        .thenReturn(List.of(demand(SNAPSHOT_ON, "Java", 14), demand(SNAPSHOT_ON, "SQL", 9)));
  }

  private static SkillDailyDemandEntity demand(LocalDate snapshotOn, String skill, int jobPosts) {
    return new SkillDailyDemandEntity(snapshotOn, skill, jobPosts);
  }

  private static CategoryDailySalaryEntity salary(String category, String workType, int n,
      Double median) {
    return new CategoryDailySalaryEntity(SNAPSHOT_ON, category, workType, "ARS", n, median,
        median, median);
  }

  private static PublishedCount published(String publishedOn, long jobPosts) {
    return new PublishedCount() {
      @Override
      public LocalDate getPublishedOn() {
        return LocalDate.parse(publishedOn);
      }

      @Override
      public long getJobPosts() {
        return jobPosts;
      }
    };
  }
}
