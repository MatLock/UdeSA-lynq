package com.lynq.bff.exceptions;

public class BadGatewayException extends RuntimeException {

  public BadGatewayException(String message, Throwable cause) {
    super(message, cause);
  }
}
