package com.lynq.backend.controller.response;


import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ErrorRestResponse<T> extends GlobalRestResponse<T>{

  private String reason;

  public ErrorRestResponse(T data, String reason) {
    super(false, data);
    this.reason = reason;
  }
}
