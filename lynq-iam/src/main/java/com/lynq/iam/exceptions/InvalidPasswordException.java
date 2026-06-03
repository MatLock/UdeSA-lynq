package com.lynq.iam.exceptions;

public class InvalidPasswordException extends RuntimeException{

  public InvalidPasswordException(String message){
    super(message);
  }

}
