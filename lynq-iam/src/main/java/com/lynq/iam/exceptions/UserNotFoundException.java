package com.lynq.iam.exceptions;

public class UserNotFoundException extends RuntimeException{

  public UserNotFoundException(String message){
    super(message);
  }

}
