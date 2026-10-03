package com.lynq.analytics.cache;

import java.util.Set;

public final class AnalyticsCaches {

  public static final String TIME_TO_FILL = "time-to-fill";
  public static final String STANDING = "standing";
  public static final String SALARY = "salary";
  public static final String BENCHMARK = "benchmark";
  public static final String MARKET = "market";
  public static final String COMPANY_JOBS = "company-jobs";

  public static final Set<String> ALL =
      Set.of(TIME_TO_FILL, STANDING, SALARY, BENCHMARK, MARKET, COMPANY_JOBS);

  private AnalyticsCaches() {
  }
}
