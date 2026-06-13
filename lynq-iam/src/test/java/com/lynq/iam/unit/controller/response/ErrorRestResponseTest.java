package com.lynq.iam.unit.controller.response;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

class ErrorRestResponseTest {

  private static final boolean EXPECTED_ERROR_SUCCESS_FLAG = false;
  private static final String SAMPLE_REASON = "Missing required header";
  private static final String SAMPLE_DATA_PAYLOAD = "additional-error-context";
  private static final Object NO_DATA = null;

  @Test
  void noArgsConstructorProducesNullReasonAndDefaultParentFields() {
    ErrorRestResponse<String> response = new ErrorRestResponse<>();

    assertThat(response.getReason(), is(nullValue()));
    assertThat(response.getData(), is(nullValue()));
    assertThat(response.isSuccess(), is(EXPECTED_ERROR_SUCCESS_FLAG));
  }

  @Test
  void twoArgConstructorAssignsDataAndReasonAndForcesSuccessFalse() {
    ErrorRestResponse<String> response = new ErrorRestResponse<>(SAMPLE_DATA_PAYLOAD, SAMPLE_REASON);

    assertThat(response.getData(), is(SAMPLE_DATA_PAYLOAD));
    assertThat(response.getReason(), is(SAMPLE_REASON));
    assertThat(response.isSuccess(), is(EXPECTED_ERROR_SUCCESS_FLAG));
  }

  @Test
  void twoArgConstructorAcceptsNullDataAndStillForcesSuccessFalse() {
    ErrorRestResponse<String> response = new ErrorRestResponse<>((String) NO_DATA, SAMPLE_REASON);

    assertThat(response.getData(), is(nullValue()));
    assertThat(response.getReason(), is(SAMPLE_REASON));
    assertThat(response.isSuccess(), is(EXPECTED_ERROR_SUCCESS_FLAG));
  }

  @Test
  void errorRestResponseIsAGlobalRestResponse() {
    ErrorRestResponse<String> response = new ErrorRestResponse<>(SAMPLE_DATA_PAYLOAD, SAMPLE_REASON);

    assertThat(response, is(instanceOf(GlobalRestResponse.class)));
  }

  @Test
  void setterUpdatesReasonField() {
    ErrorRestResponse<String> response = new ErrorRestResponse<>();

    response.setReason(SAMPLE_REASON);

    assertThat(response.getReason(), is(SAMPLE_REASON));
  }
}