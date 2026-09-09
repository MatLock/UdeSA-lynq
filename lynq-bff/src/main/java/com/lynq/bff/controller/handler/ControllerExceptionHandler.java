package com.lynq.bff.controller.handler;

import com.lynq.bff.controller.response.ErrorRestResponse;
import com.lynq.bff.exceptions.BadGatewayException;
import com.lynq.bff.exceptions.BadRequestException;
import com.lynq.bff.exceptions.ForbiddenException;
import com.lynq.bff.exceptions.MethodNotAllowedException;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.authorization.ExpressionAuthorizationDecision;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestControllerAdvice
@Log4j2
public class ControllerExceptionHandler extends ResponseEntityExceptionHandler {

  private static final String DMZ_UNAVAILABLE_ERROR = "Downstream service is unavailable";
  private static final String UNEXPECTED_ERROR = "Unexpected error while proxying the request";
  private static final String ONLY_ROLE_CAN_PERFORM = "Only users of type %s can perform this action";
  private static final String ACCESS_DENIED = "The authenticated user is not allowed to perform this action";
  private static final Pattern REQUIRED_ROLE = Pattern.compile("hasRole\\('([^']+)'\\)");

  @ExceptionHandler(BadGatewayException.class)
  public ResponseEntity<ErrorRestResponse<Void>> handleBadGateway(BadGatewayException ex) {
    log.error("message= DMZ service unreachable", ex);
    return ResponseEntity
        .status(HttpStatus.BAD_GATEWAY)
        .body(new ErrorRestResponse<>(null, DMZ_UNAVAILABLE_ERROR));
  }

  @ExceptionHandler(BadRequestException.class)
  public ResponseEntity<ErrorRestResponse<Void>> handleBadRequest(BadRequestException ex) {
    log.warn("message= Request cannot be served, reason={}", ex.getMessage());
    return ResponseEntity
        .status(HttpStatus.BAD_REQUEST)
        .body(new ErrorRestResponse<>(null, ex.getMessage()));
  }

  @ExceptionHandler(ForbiddenException.class)
  public ResponseEntity<ErrorRestResponse<Void>> handleForbidden(ForbiddenException ex) {
    log.warn("message= Endpoint not reachable through the gateway, reason={}", ex.getMessage());
    return ResponseEntity
        .status(HttpStatus.FORBIDDEN)
        .body(new ErrorRestResponse<>(null, ex.getMessage()));
  }

  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<ErrorRestResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
    log.warn("message= Access denied, reason={}", ex.getMessage());
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
