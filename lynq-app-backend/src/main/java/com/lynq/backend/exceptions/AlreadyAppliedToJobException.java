package com.lynq.backend.exceptions;

public class AlreadyAppliedToJobException extends RuntimeException {

  public AlreadyAppliedToJobException(String message) {
    super(message);
  }
}
