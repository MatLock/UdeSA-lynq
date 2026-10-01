package com.lynq.bff.service;

import com.lynq.bff.client.LynqAnalyticsClient;
import com.lynq.bff.client.response.JobSalaryResponse;
import com.lynq.bff.client.response.JobStandingResponse;
import com.lynq.bff.client.response.JobTimeToFillResponse;
import org.springframework.stereotype.Service;

@Service
public class AnalyticsService {

  private static final String TIME_TO_FILL_UNREADABLE = "The time to fill could not be read";
  private static final String STANDING_UNREADABLE = "The standing could not be read";
  private static final String SALARY_UNREADABLE = "The salary insights could not be read";

  private final LynqAnalyticsClient lynqAnalyticsClient;

  public AnalyticsService(LynqAnalyticsClient lynqAnalyticsClient) {
    this.lynqAnalyticsClient = lynqAnalyticsClient;
  }

  public JobTimeToFillResponse getTimeToFill(String jobId, Caller caller) {
    return DownstreamErrors.call(
        () -> lynqAnalyticsClient
            .getTimeToFill(jobId, caller.requestUuid(), caller.authorization())
            .getData(),
        TIME_TO_FILL_UNREADABLE);
  }

  public JobStandingResponse getStanding(String jobId, Caller caller) {
    return DownstreamErrors.call(
        () -> lynqAnalyticsClient
            .getStanding(jobId, caller.requestUuid(), caller.authorization())
            .getData(),
        STANDING_UNREADABLE);
  }

  public JobSalaryResponse getSalary(String jobId, Caller caller) {
    return DownstreamErrors.call(
        () -> lynqAnalyticsClient
            .getSalary(jobId, caller.requestUuid(), caller.authorization())
            .getData(),
        SALARY_UNREADABLE);
  }
}
