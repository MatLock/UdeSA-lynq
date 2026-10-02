package com.lynq.backend.controller.impl;

import com.lynq.backend.controller.response.GlobalRestResponse;
import com.lynq.backend.controller.response.ReplayEventsRestResponse;
import com.lynq.backend.service.DomainEventReplayService;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.sameInstance;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InternalEventsControllerImplTest {

  @Mock
  private DomainEventReplayService domainEventReplayService;

  private InternalEventsControllerImpl controller;

  @BeforeEach
  void setUp() {
    controller = new InternalEventsControllerImpl(domainEventReplayService);
  }

  @Test
  void replayAnswersWithWhatTheReplayPublished() {
    ReplayEventsRestResponse replayed = ReplayEventsRestResponse.builder()
        .published(12)
        .failed(1)
        .skippedJobPosts(2)
        .publishedByEventType(Map.of("JobPostPublished", 12))
        .build();
    when(domainEventReplayService.replay()).thenReturn(replayed);

    ResponseEntity<GlobalRestResponse<ReplayEventsRestResponse>> response = controller.replay();

    assertThat(response.getStatusCode(), is(HttpStatus.OK));
    assertThat(response.getBody().isSuccess(), is(true));
    assertThat(response.getBody().getData(), is(sameInstance(replayed)));
  }
}
