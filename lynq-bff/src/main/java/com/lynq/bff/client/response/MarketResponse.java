package com.lynq.bff.client.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.time.LocalDate;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class MarketResponse {

  private LocalDate snapshotOn;
  private Integer openJobPosts;
  private Integer openWithSalary;
  private List<SkillDemandResponse> skillDemand;
  private MarketSalaryResponse salary;
  private List<WeeklyCountResponse> publishedPerWeek;
}
