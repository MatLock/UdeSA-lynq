package com.lynq.bff.exceptions;

public class MethodNotAllowedException extends RuntimeException {

  public MethodNotAllowedException(String message) {
    super(message);
  }
}
