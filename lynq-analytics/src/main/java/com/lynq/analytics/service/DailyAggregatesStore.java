package com.lynq.analytics.service;

import com.lynq.analytics.model.CategoryDailySalaryEntity;
import com.lynq.analytics.model.JobDailyStatsEntity;
import com.lynq.analytics.model.SkillDailyDemandEntity;
import com.lynq.analytics.repository.CategoryDailySalaryRepository;
import com.lynq.analytics.repository.JobDailyStatsRepository;
import com.lynq.analytics.repository.SkillDailyDemandRepository;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class DailyAggregatesStore {

  private final JobDailyStatsRepository jobDailyStatsRepository;
  private final SkillDailyDemandRepository skillDailyDemandRepository;
  private final CategoryDailySalaryRepository categoryDailySalaryRepository;

  public DailyAggregatesStore(JobDailyStatsRepository jobDailyStatsRepository,
      SkillDailyDemandRepository skillDailyDemandRepository,
      CategoryDailySalaryRepository categoryDailySalaryRepository) {
    this.jobDailyStatsRepository = jobDailyStatsRepository;
    this.skillDailyDemandRepository = skillDailyDemandRepository;
    this.categoryDailySalaryRepository = categoryDailySalaryRepository;
  }

  @Transactional
  public void replace(LocalDate snapshotOn, List<JobDailyStatsEntity> jobStats,
      List<SkillDailyDemandEntity> skillDemand, List<CategoryDailySalaryEntity> salaries) {
    jobDailyStatsRepository.deleteSnapshotOf(snapshotOn);
    skillDailyDemandRepository.deleteSnapshotOf(snapshotOn);
    categoryDailySalaryRepository.deleteSnapshotOf(snapshotOn);
    jobDailyStatsRepository.saveAll(jobStats);
    skillDailyDemandRepository.saveAll(skillDemand);
    categoryDailySalaryRepository.saveAll(salaries);
  }
}
