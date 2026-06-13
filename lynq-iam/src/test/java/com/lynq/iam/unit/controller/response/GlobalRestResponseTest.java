package com.lynq.iam.unit.controller.response;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

class GlobalRestResponseTest {

  private static final boolean SUCCESS_TRUE = true;
  private static final boolean SUCCESS_FALSE = false;
  private static final String SAMPLE_DATA_PAYLOAD = "sample-data-payload";
  private static final Integer SAMPLE_NUMERIC_PAYLOAD = 42;

  @Test
  void allArgsConstructorAssignsAllFields() {
    GlobalRestResponse<String> response = new GlobalRestResponse<>(SUCCESS_TRUE, SAMPLE_DATA_PAYLOAD);

    assertThat(response.isSuccess(), is(SUCCESS_TRUE));
    assertThat(response.getData(), is(SAMPLE_DATA_PAYLOAD));
  }

  @Test
  void noArgsConstructorProducesDefaultFields() {
    GlobalRestResponse<String> response = new GlobalRestResponse<>();

    assertThat(response.isSuccess(), is(SUCCESS_FALSE));
    assertThat(response.getData(), is(nullValue()));
  }

  @Test
  void builderProducesEquivalentInstance() {
    GlobalRestResponse<Integer> built = GlobalRestResponse.<Integer>builder()
        .success(SUCCESS_TRUE)
        .data(SAMPLE_NUMERIC_PAYLOAD)
        .build();

    assertThat(built, is(notNullValue()));
    assertThat(built.isSuccess(), is(SUCCESS_TRUE));
    assertThat(built.getData(), is(SAMPLE_NUMERIC_PAYLOAD));
  }

  @Test
  void settersUpdateFieldValues() {
    GlobalRestResponse<String> response = new GlobalRestResponse<>();

    response.setSuccess(SUCCESS_TRUE);
    response.setData(SAMPLE_DATA_PAYLOAD);

    assertThat(response.isSuccess(), is(SUCCESS_TRUE));
    assertThat(response.getData(), is(SAMPLE_DATA_PAYLOAD));
  }
}