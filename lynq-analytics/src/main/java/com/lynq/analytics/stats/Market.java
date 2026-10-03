package com.lynq.analytics.stats;

import java.time.LocalDate;
import java.util.List;

public record Market(
    LocalDate snapshotOn,
    int openJobPosts,
    int openWithSalary,
    List<SkillDemand> skillDemand,
    MarketSalary salary,
    List<WeeklyCount> publishedPerWeek) {

  public record SkillDemand(String skill, int openJobPosts, Integer weeklyChange) {
  }

  public record MarketSalary(String currency, List<CategorySalary> rows) {
  }

  public record CategorySalary(String category, String workType, int n, Double median, Double p25,
      Double p75, boolean insufficientData) {
  }

  public record WeeklyCount(LocalDate weekStart, int jobPosts) {
  }
}
