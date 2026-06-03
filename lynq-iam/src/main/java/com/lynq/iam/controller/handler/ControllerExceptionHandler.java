package com.lynq.iam.controller.handler;

import com.lynq.iam.controller.response.ErrorRestResponse;
import com.lynq.iam.exceptions.InvalidPasswordException;
import com.lynq.iam.exceptions.UserNotFoundException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import lombok.extern.log4j.Log4j2;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
@Log4j2
public class ControllerExceptionHandler extends ResponseEntityExceptionHandler {

  private static final String INVALID_FIELDS_ERROR_MSG = "Invalid Fields Found";

  @ExceptionHandler(UserNotFoundException.class)
  public ResponseEntity<ErrorRestResponse<Void>> handleUserNotFound(UserNotFoundException ex) {
    log.error("message= User not found", ex);
    return ResponseEntity
        .status(HttpStatus.FORBIDDEN)
        .body(new ErrorRestResponse<>(null, ex.getMessage()));
  }

  @ExceptionHandler(InvalidPasswordException.class)
  public ResponseEntity<ErrorRestResponse<Void>> handleInvalidPassword(InvalidPasswordException ex) {
    log.error("message= Invalid password", ex);
    return ResponseEntity
        .status(HttpStatus.FORBIDDEN)
        .body(new ErrorRestResponse<>(null, ex.getMessage()));
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ErrorRestResponse<Void>> handleIllegalArgument(IllegalArgumentException ex) {
    log.error("message= Illegal argument", ex);
    return ResponseEntity
        .status(HttpStatus.CONFLICT)
        .body(new ErrorRestResponse<>(null, ex.getMessage()));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorRestResponse<Void>> handleGeneral(Exception ex) {
    log.error("message= Unexpected error", ex);
    return ResponseEntity
        .status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(new ErrorRestResponse<>(null, ex.getMessage()));
  }

  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
    log.error("message= Method argument not valid", ex);
    Map<String, String> errors = new HashMap<>();
    ex.getBindingResult().getAllErrors().forEach((error) -> {
      String fieldName = ((FieldError) error).getField();
      String errorMessage = error.getDefaultMessage();
      errors.put(fieldName, errorMessage);
    });
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(new ErrorRestResponse<>(errors,INVALID_FIELDS_ERROR_MSG ));
  }
}