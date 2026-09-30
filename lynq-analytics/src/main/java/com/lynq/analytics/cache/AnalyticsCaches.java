package com.lynq.analytics.cache;

import java.util.Set;

public final class AnalyticsCaches {

  public static final String TIME_TO_FILL = "time-to-fill";
  public static final String STANDING = "standing";
  public static final String SALARY = "salary";

  public static final Set<String> ALL = Set.of(TIME_TO_FILL, STANDING, SALARY);

  private AnalyticsCaches() {
  }
}
