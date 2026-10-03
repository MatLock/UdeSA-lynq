package com.lynq.analytics.stats;

import java.time.LocalDate;
import java.util.List;

public record CompanyJobs(List<CompanyJob> jobs) {

  public record CompanyJob(String jobId, String title, String status, LocalDate publishedOn,
      int applications, Double medianScore, boolean insufficientData) {
  }
}
