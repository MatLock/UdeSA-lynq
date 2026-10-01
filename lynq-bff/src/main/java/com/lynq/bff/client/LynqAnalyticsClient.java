package com.lynq.bff.client;

import com.lynq.bff.client.response.JobSalaryResponse;
import com.lynq.bff.client.response.JobStandingResponse;
import com.lynq.bff.client.response.JobTimeToFillResponse;
import com.lynq.bff.controller.response.GlobalRestResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "lynqAnalytics", url = "${lynq.analytics.url}")
public interface LynqAnalyticsClient {

  String REQUEST_UUID_HEADER = "lynq-request-uuid";
  String AUTHORIZATION_HEADER = "Authorization";

  @GetMapping("/dmz/analytics/job/{jobId}/time-to-fill")
  GlobalRestResponse<JobTimeToFillResponse> getTimeToFill(
      @PathVariable("jobId") String jobId,
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid,
      @RequestHeader(AUTHORIZATION_HEADER) String authorization);

  @GetMapping("/dmz/analytics/job/{jobId}/standing")
  GlobalRestResponse<JobStandingResponse> getStanding(
      @PathVariable("jobId") String jobId,
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid,
      @RequestHeader(AUTHORIZATION_HEADER) String authorization);

  @GetMapping("/dmz/analytics/job/{jobId}/salary")
  GlobalRestResponse<JobSalaryResponse> getSalary(
      @PathVariable("jobId") String jobId,
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid,
      @RequestHeader(AUTHORIZATION_HEADER) String authorization);
}
