package com.lynq.bff.controller.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class ErrorRestResponse<T> extends GlobalRestResponse<T> {

  private String reason;

  @JsonInclude(JsonInclude.Include.NON_NULL)
  private String code;

  public ErrorRestResponse(T data, String reason) {
    super(false, data);
    this.reason = reason;
  }

  public ErrorRestResponse(T data, String reason, String code) {
    super(false, data);
    this.reason = reason;
    this.code = code;
  }
}
