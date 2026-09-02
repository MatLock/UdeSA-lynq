package com.lynq.bff.service;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.lynq.bff.client.LynqBackendClient;
import com.lynq.bff.client.response.UserResponse;
import com.lynq.bff.controller.response.GlobalRestResponse;
import com.lynq.bff.exceptions.BadGatewayException;
import com.lynq.bff.exceptions.ForbiddenException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CandidateReaderTest {

  private static final String USER_ID = "11111111-1111-1111-1111-111111111111";
  private static final String REQUEST_UUID = "018f9c3a-2b1d-7c4e-9a6f-1e2d3c4b5a99";
  private static final String AUTHORIZATION = "Bearer access-token";
  private static final Caller CALLER = new Caller(USER_ID, REQUEST_UUID, AUTHORIZATION);
  private static final String AVATAR_URL = "https://lynq-bucket.s3/avatar.png";
  private static final String ONLY_CANDIDATES = "Only users of type CANDIDATE can do this";

  @Mock
  private LynqBackendClient lynqBackendClient;

  private CandidateReader candidateReader;

  @BeforeEach
  void setUp() {
    candidateReader = new CandidateReader(lynqBackendClient);
  }

  @Test
  void readReturnsTheCandidateLynqAppBackendKnows() {
    when(lynqBackendClient.getUser(REQUEST_UUID, AUTHORIZATION))
        .thenReturn(new GlobalRestResponse<>(true, UserResponse.builder()
            .id(USER_ID)
            .userType("CANDIDATE")
            .userProfileImageUrl(AVATAR_URL)
            .build()));

    UserResponse user = candidateReader.read(CALLER);

    assertThat(user.getId(), is(USER_ID));
    assertThat(user.getUserProfileImageUrl(), is(AVATAR_URL));
  }

  @Test
  void readRefusesAUserThatIsNotACandidate() {
    when(lynqBackendClient.getUser(REQUEST_UUID, AUTHORIZATION))
        .thenReturn(new GlobalRestResponse<>(true, UserResponse.builder()
            .id(USER_ID)
            .userType("COMPANY")
            .build()));

    ForbiddenException exception =
        assertThrows(ForbiddenException.class, () -> candidateReader.read(CALLER));

    assertThat(exception.getMessage(), is(ONLY_CANDIDATES));
  }

  @Test
  void readRefusesACallerLynqAppBackendDoesNotKnow() {
    when(lynqBackendClient.getUser(REQUEST_UUID, AUTHORIZATION))
        .thenReturn(new GlobalRestResponse<>(true, null));

    assertThrows(ForbiddenException.class, () -> candidateReader.read(CALLER));
  }

  @Test
  void readReportsABadGatewayWhenLynqAppBackendCannotBeReached() {
    when(lynqBackendClient.getUser(REQUEST_UUID, AUTHORIZATION))
        .thenThrow(new IllegalStateException("backend down"));

    assertThrows(BadGatewayException.class, () -> candidateReader.read(CALLER));
  }
}
