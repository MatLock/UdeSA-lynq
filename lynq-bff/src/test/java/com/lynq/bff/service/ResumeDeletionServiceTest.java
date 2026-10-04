package com.lynq.bff.service;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lynq.bff.client.LynqBackendClient;
import com.lynq.bff.exceptions.BadGatewayException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ResumeDeletionServiceTest {

  private static final String USER_ID = "11111111-1111-1111-1111-111111111111";
  private static final String REQUEST_UUID = "018f9c3a-2b1d-7c4e-9a6f-1e2d3c4b5a99";
  private static final String AUTHORIZATION = "Bearer access-token";
  private static final Caller CALLER = new Caller(USER_ID, REQUEST_UUID, AUTHORIZATION);

  private static final String RESUME_ID = "018f9c3a-2b1d-7c4e-9a6f-1e2d3c4b5a60";

  @Mock
  private LynqBackendClient lynqBackendClient;

  private ResumeDeletionService resumeDeletionService;

  @BeforeEach
  void setUp() {
    resumeDeletionService = new ResumeDeletionService(lynqBackendClient);
  }

  @Test
  void deletesTheResumeInTheBackend() {
    resumeDeletionService.delete(RESUME_ID, CALLER);

    verify(lynqBackendClient).deleteResume(RESUME_ID, REQUEST_UUID, AUTHORIZATION);
  }

  @Test
  void failsWithBadGatewayWhenTheBackendCannotDeleteTheResume() {
    when(lynqBackendClient.deleteResume(RESUME_ID, REQUEST_UUID, AUTHORIZATION))
        .thenThrow(new IllegalStateException("boom"));

    BadGatewayException exception = assertThrows(BadGatewayException.class,
        () -> resumeDeletionService.delete(RESUME_ID, CALLER));

    assertThat(exception.getMessage(), is("The resume could not be deleted"));
  }

}
