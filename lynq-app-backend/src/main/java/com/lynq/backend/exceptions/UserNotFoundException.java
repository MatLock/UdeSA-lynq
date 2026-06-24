package com.lynq.backend.exceptions;

public class UserNotFoundException extends RuntimeException{

  public UserNotFoundException(String message){
    super(message);
  }

}
