package com.lynq.analytics.exceptions;

public class ForbiddenException extends RuntimeException{

  public ForbiddenException(String message){
    super(message);
  }
}
