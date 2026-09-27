package com.lynq.bff.service;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.lynq.bff.exceptions.BadGatewayException;
import com.lynq.bff.exceptions.BadRequestException;
import com.lynq.bff.exceptions.ConflictException;
import com.lynq.bff.exceptions.ForbiddenException;
import com.lynq.bff.exceptions.MethodNotAllowedException;
import com.lynq.bff.exceptions.NotFoundException;
import com.lynq.bff.exceptions.UnauthorizedException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class DownstreamErrorsTest {

  private static final String FAILURE = "The thing could not be done";
  private static final String REASON_BODY = """
      {"success": false, "reason": "The job post is already closed"}""";

  @Test
  void returnsWhatTheDownstreamCallReturned() {
    String answer = DownstreamErrors.call(() -> "payload", FAILURE);

    assertThat(answer, is("payload"));
  }

  @Test
  void runsACallThatAnswersNothing() {
    StringBuilder ran = new StringBuilder();

    DownstreamErrors.run(() -> ran.append("ran"), FAILURE);

    assertThat(ran.toString(), is("ran"));
  }

  static List<Arguments> statuses() {
    return List.of(
        Arguments.of(400, BadRequestException.class),
        Arguments.of(401, UnauthorizedException.class),
        Arguments.of(403, ForbiddenException.class),
        Arguments.of(404, NotFoundException.class),
        Arguments.of(405, MethodNotAllowedException.class),
        Arguments.of(409, ConflictException.class));
  }

  /**
   * The status the downstream service answered decides the status the caller sees. Turning all of
   * these into a bad gateway is what the typed clients would do on their own, and it would tell a
   * caller their own bad request was the gateway's fault.
   */
  @ParameterizedTest
  @MethodSource("statuses")
  void keepsTheStatusTheDownstreamServiceAnswered(int status, Class<?> expected) {
    RuntimeException thrown = assertThrows(RuntimeException.class,
        () -> DownstreamErrors.call(failing(status, REASON_BODY), FAILURE));

    assertThat(thrown, is(instanceOf(expected)));
  }

  @ParameterizedTest
  @MethodSource("statuses")
  void carriesTheReasonTheDownstreamServiceGave(int status, Class<?> ignored) {
    RuntimeException thrown = assertThrows(RuntimeException.class,
        () -> DownstreamErrors.call(failing(status, REASON_BODY), FAILURE));

    assertThat(thrown.getMessage(), is("The job post is already closed"));
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "not json at all", "{\"data\": null}"})
  void fallsBackToItsOwnMessageWhenTheBodyNamesNoReason(String body) {
    NotFoundException thrown = assertThrows(NotFoundException.class,
        () -> DownstreamErrors.call(failing(404, body), FAILURE));

    assertThat(thrown.getMessage(), is(FAILURE));
  }

  /** feign answers an empty body, never a null one, when the service sent none. */
  @Test
  void fallsBackToItsOwnMessageWhenTheDownstreamServiceSendsNoBodyAtAll() {
    NotFoundException thrown = assertThrows(NotFoundException.class,
        () -> DownstreamErrors.call(() -> {
          throw FeignErrors.status(404);
        }, FAILURE));

    assertThat(thrown.getMessage(), is(FAILURE));
  }

  @Test
  void carriesTheConflictCodeWhenTheDownstreamServiceGivesOne() {
    String body = """
        {"success": false, "reason": "Already applied", "code": "ALREADY_APPLIED"}""";

    ConflictException thrown = assertThrows(ConflictException.class,
        () -> DownstreamErrors.call(failing(409, body), FAILURE));

    assertThat(thrown.getCode(), is("ALREADY_APPLIED"));
  }

  @Test
  void leavesTheConflictCodeUnsetWhenTheDownstreamServiceGivesNone() {
    ConflictException thrown = assertThrows(ConflictException.class,
        () -> DownstreamErrors.call(failing(409, REASON_BODY), FAILURE));

    assertThat(thrown.getCode(), is(nullValue()));
  }

  /**
   * A status the gateway does not map is the downstream service failing on its own account, not
   * the caller's: a 500 it did not expect, or a 503 while it restarts. The caller is told the
   * gateway could not get an answer, and the reason the service gave is not repeated to them.
   */
  @ParameterizedTest
  @ValueSource(ints = {500, 502, 503, 504, 418})
  void answersBadGatewayForAStatusItDoesNotMap(int status) {
    BadGatewayException thrown = assertThrows(BadGatewayException.class,
        () -> DownstreamErrors.call(failing(status, REASON_BODY), FAILURE));

    assertThat(thrown.getMessage(), is(FAILURE));
  }

  @Test
  void answersBadGatewayWhenTheServiceCouldNotBeReachedAtAll() {
    BadGatewayException thrown = assertThrows(BadGatewayException.class,
        () -> DownstreamErrors.call(() -> {
          throw FeignErrors.unreachable();
        }, FAILURE));

    assertThat(thrown.getMessage(), is(FAILURE));
  }

  @Test
  void answersBadGatewayWhenTheCallFailsForAReasonThatIsNotTheDownstreamService() {
    BadGatewayException thrown = assertThrows(BadGatewayException.class,
        () -> DownstreamErrors.call(() -> {
          throw new IllegalStateException("the decoder blew up");
        }, FAILURE));

    assertThat(thrown.getMessage(), is(FAILURE));
    assertThat(thrown.getCause(), is(instanceOf(IllegalStateException.class)));
  }

  private static java.util.function.Supplier<String> failing(int status, String body) {
    return () -> {
      throw FeignErrors.status(status, body);
    };
  }
}
