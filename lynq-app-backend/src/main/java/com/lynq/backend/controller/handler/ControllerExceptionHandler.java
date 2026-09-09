package com.lynq.backend.controller.handler;

import com.lynq.backend.controller.response.ErrorRestResponse;
import com.lynq.backend.exceptions.AlreadyAppliedToJobException;
import com.lynq.backend.exceptions.BadRequestException;
import com.lynq.backend.exceptions.ForbiddenException;
import com.lynq.backend.exceptions.NotFoundException;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.authorization.ExpressionAuthorizationDecision;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestControllerAdvice
@Log4j2
public class ControllerExceptionHandler extends ResponseEntityExceptionHandler {

  private static final String INVALID_FIELDS_ERROR_MSG = "Invalid Fields Found";
  private static final String ONLY_ROLE_CAN_PERFORM = "Only users of type %s can perform this action";
  private static final String ACCESS_DENIED = "The authenticated user is not allowed to perform this action";
  private static final Pattern REQUIRED_ROLE = Pattern.compile("hasRole\\('([^']+)'\\)");

  @ExceptionHandler(ForbiddenException.class)
  public ResponseEntity<ErrorRestResponse<Void>> handleForbidden(ForbiddenException ex) {
    log.error("message= Forbidden", ex);
    return ResponseEntity
        .status(HttpStatus.FORBIDDEN)
        .body(new ErrorRestResponse<>(null, ex.getMessage()));
  }

  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<ErrorRestResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
    log.error("message= Access denied", ex);
    return ResponseEntity
        .status(HttpStatus.FORBIDDEN)
        .body(new ErrorRestResponse<>(null, accessDeniedReason(ex)));
  }

  private static String accessDeniedReason(AccessDeniedException ex) {
    if (ex instanceof AuthorizationDeniedException denied
        && denied.getAuthorizationResult() instanceof ExpressionAuthorizationDecision decision) {
      Matcher requiredRole = REQUIRED_ROLE.matcher(decision.getExpression().getExpressionString());
      if (requiredRole.find()) {
        return String.format(ONLY_ROLE_CAN_PERFORM, requiredRole.group(1));
      }
    }
    return ACCESS_DENIED;
  }

  @ExceptionHandler(BadRequestException.class)
  public ResponseEntity<ErrorRestResponse<Void>> handleBadRequest(BadRequestException ex) {
    log.error("message= Bad request", ex);
    return ResponseEntity
        .status(HttpStatus.BAD_REQUEST)
        .body(new ErrorRestResponse<>(null, ex.getMessage()));
  }

  @ExceptionHandler(AlreadyAppliedToJobException.class)
  public ResponseEntity<ErrorRestResponse<Void>> handleAlreadyAppliedToJob(
      AlreadyAppliedToJobException ex) {
    log.error("message= Already applied to job", ex);
    return ResponseEntity
        .status(HttpStatus.BAD_REQUEST)
        .body(new ErrorRestResponse<>(null, ex.getMessage()));
  }

  @ExceptionHandler(NotFoundException.class)
  public ResponseEntity<ErrorRestResponse<Void>> handleNotFound(NotFoundException ex) {
    log.error("message= Not found", ex);
    return ResponseEntity
        .status(HttpStatus.NOT_FOUND)
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
    ex.getBindingResult().getAllErrors().forEach(error -> {
      String fieldName = ((FieldError) error).getField();
      String errorMessage = error.getDefaultMessage();
      errors.put(fieldName, errorMessage);
    });
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(new ErrorRestResponse<>(errors,INVALID_FIELDS_ERROR_MSG ));
  }
}