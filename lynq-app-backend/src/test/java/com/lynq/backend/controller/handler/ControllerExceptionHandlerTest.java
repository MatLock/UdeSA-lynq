package com.lynq.backend.controller.handler;

import com.lynq.backend.controller.response.ErrorRestResponse;
import com.lynq.backend.exceptions.BadRequestException;
import com.lynq.backend.exceptions.ForbiddenException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ControllerExceptionHandlerTest {

  private static final String ERROR_MESSAGE = "something went wrong";
  private static final String INVALID_FIELDS_ERROR_MSG = "Invalid Fields Found";
  private static final String FIELD_NAME = "companyName";
  private static final String FIELD_ERROR_MESSAGE = "must not be blank";

  private ControllerExceptionHandler handler;

  @BeforeEach
  void setUp() {
    handler = new ControllerExceptionHandler();
  }

  @Test
  void handleForbiddenReturnsForbiddenStatusWithMessage() {
    ForbiddenException exception = mock(ForbiddenException.class);
    when(exception.getMessage()).thenReturn(ERROR_MESSAGE);

    ResponseEntity<ErrorRestResponse<Void>> response = handler.handleForbidden(exception);

    assertThat(response.getStatusCode(), is(HttpStatus.FORBIDDEN));
    assertThat(response.getBody().isSuccess(), is(false));
    assertThat(response.getBody().getReason(), is(ERROR_MESSAGE));
    assertThat(response.getBody().getData(), is(nullValue()));
  }

  @Test
  void handleBadRequestReturnsBadRequestStatusWithMessage() {
    BadRequestException exception = mock(BadRequestException.class);
    when(exception.getMessage()).thenReturn(ERROR_MESSAGE);

    ResponseEntity<ErrorRestResponse<Void>> response = handler.handleBadRequest(exception);

    assertThat(response.getStatusCode(), is(HttpStatus.BAD_REQUEST));
    assertThat(response.getBody().isSuccess(), is(false));
    assertThat(response.getBody().getReason(), is(ERROR_MESSAGE));
    assertThat(response.getBody().getData(), is(nullValue()));
  }

  @Test
  void handleIllegalArgumentReturnsConflictStatusWithMessage() {
    IllegalArgumentException exception = mock(IllegalArgumentException.class);
    when(exception.getMessage()).thenReturn(ERROR_MESSAGE);

    ResponseEntity<ErrorRestResponse<Void>> response = handler.handleIllegalArgument(exception);

    assertThat(response.getStatusCode(), is(HttpStatus.CONFLICT));
    assertThat(response.getBody().isSuccess(), is(false));
    assertThat(response.getBody().getReason(), is(ERROR_MESSAGE));
    assertThat(response.getBody().getData(), is(nullValue()));
  }

  @Test
  void handleGeneralReturnsInternalServerErrorStatusWithMessage() {
    Exception exception = mock(Exception.class);
    when(exception.getMessage()).thenReturn(ERROR_MESSAGE);

    ResponseEntity<ErrorRestResponse<Void>> response = handler.handleGeneral(exception);

    assertThat(response.getStatusCode(), is(HttpStatus.INTERNAL_SERVER_ERROR));
    assertThat(response.getBody().isSuccess(), is(false));
    assertThat(response.getBody().getReason(), is(ERROR_MESSAGE));
    assertThat(response.getBody().getData(), is(nullValue()));
  }

  @Test
  void handleMethodArgumentNotValidReturnsBadRequestWithFieldErrorMap() {
    MethodArgumentNotValidException exception = mock(MethodArgumentNotValidException.class);
    BindingResult bindingResult = mock(BindingResult.class);
    FieldError fieldError = mock(FieldError.class);
    when(exception.getBindingResult()).thenReturn(bindingResult);
    when(bindingResult.getAllErrors()).thenReturn(List.<ObjectError>of(fieldError));
    when(fieldError.getField()).thenReturn(FIELD_NAME);
    when(fieldError.getDefaultMessage()).thenReturn(FIELD_ERROR_MESSAGE);

    ResponseEntity<Object> response =
        handler.handleMethodArgumentNotValid(exception, null, null, null);

    assertThat(response.getStatusCode(), is(HttpStatus.BAD_REQUEST));
    ErrorRestResponse<?> body = (ErrorRestResponse<?>) response.getBody();
    assertThat(body.isSuccess(), is(false));
    assertThat(body.getReason(), is(INVALID_FIELDS_ERROR_MSG));

    @SuppressWarnings("unchecked")
    Map<String, String> errors = (Map<String, String>) body.getData();
    assertThat(errors.get(FIELD_NAME), is(FIELD_ERROR_MESSAGE));
  }
}