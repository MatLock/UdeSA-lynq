package com.lynq.backend.controller.request;

import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class UpdateCompanyRequest {

  private String name;
  private String about;
  @Min(1)
  private Integer size;

}
