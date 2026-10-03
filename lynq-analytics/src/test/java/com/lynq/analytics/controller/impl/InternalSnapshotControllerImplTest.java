package com.lynq.analytics.controller.impl;

import com.lynq.analytics.exceptions.ConflictException;
import com.lynq.analytics.service.DailySnapshotService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InternalSnapshotControllerImplTest {

  private static final String REQUEST_UUID = "550e8400-e29b-41d4-a716-446655440000";

  @Mock
  private DailySnapshotService dailySnapshotService;

  private InternalSnapshotControllerImpl controller;

  @BeforeEach
  void setUp() {
    controller = new InternalSnapshotControllerImpl(dailySnapshotService);
  }

  @Test
  void acceptsTheTriggerAndAnswersWithoutABody() {
    when(dailySnapshotService.start(REQUEST_UUID)).thenReturn(true);

    ResponseEntity<Void> response = controller.takeSnapshot(REQUEST_UUID);

    assertThat(response.getStatusCode(), is(HttpStatus.ACCEPTED));
    assertThat(response.getBody(), is(nullValue()));
  }

  @Test
  void refusesATriggerWhileASnapshotIsRunning() {
    when(dailySnapshotService.start(REQUEST_UUID)).thenReturn(false);

    ConflictException thrown =
        assertThrows(ConflictException.class, () -> controller.takeSnapshot(REQUEST_UUID));

    assertThat(thrown.getMessage(), is("A daily snapshot is already running"));
  }
}
