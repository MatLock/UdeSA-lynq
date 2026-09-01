package com.lynq.bff.controller.handler;

import com.lynq.bff.controller.response.ErrorRestResponse;
import com.lynq.bff.exceptions.BadGatewayException;
import com.lynq.bff.exceptions.ForbiddenException;
import com.lynq.bff.exceptions.MethodNotAllowedException;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
@Log4j2
public class ControllerExceptionHandler extends ResponseEntityExceptionHandler {

  private static final String DMZ_UNAVAILABLE_ERROR = "Downstream service is unavailable";
  private static final String UNEXPECTED_ERROR = "Unexpected error while proxying the request";

  @ExceptionHandler(BadGatewayException.class)
  public ResponseEntity<ErrorRestResponse<Void>> handleBadGateway(BadGatewayException ex) {
    log.error("message= DMZ service unreachable", ex);
    return ResponseEntity
        .status(HttpStatus.BAD_GATEWAY)
        .body(new ErrorRestResponse<>(null, DMZ_UNAVAILABLE_ERROR));
  }

  @ExceptionHandler(ForbiddenException.class)
  public ResponseEntity<ErrorRestResponse<Void>> handleForbidden(ForbiddenException ex) {
    log.warn("message= Endpoint not reachable through the gateway, reason={}", ex.getMessage());
    return ResponseEntity
        .status(HttpStatus.FORBIDDEN)
        .body(new ErrorRestResponse<>(null, ex.getMessage()));
  }

  @ExceptionHandler(MethodNotAllowedException.class)
  public ResponseEntity<ErrorRestResponse<Void>> handleMethodNotAllowed(MethodNotAllowedException ex) {
    log.error("message= Method not proxied", ex);
    return ResponseEntity
        .status(HttpStatus.METHOD_NOT_ALLOWED)
        .body(new ErrorRestResponse<>(null, ex.getMessage()));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorRestResponse<Void>> handleGeneral(Exception ex) {
    log.error("message= Unexpected error", ex);
    return ResponseEntity
        .status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(new ErrorRestResponse<>(null, UNEXPECTED_ERROR));
  }
}
