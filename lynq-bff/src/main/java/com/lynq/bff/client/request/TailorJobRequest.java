package com.lynq.bff.client.request;

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
public class TailorJobRequest {

  private String id;
  private String title;
  private String description;
  private String company;
  private String workType;
  private List<String> skills;
}
