package com.lynq.bff.client.response;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AuthUserResponseTest {

  private static final String IAM_PAYLOAD = """
      {"id":"11111111-1111-1111-1111-111111111111","accessToken":"access-token",\
      "refreshToken":"8f14e45f-ceea-467a-9ae4-9b3f4a1c2d3e"}""";

  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    objectMapper = new ObjectMapper();
    objectMapper.registerModule(new JavaTimeModule());
  }

  @Test
  void readsTheRefreshTokenFromLynqIam() throws Exception {
    AuthUserResponse response = objectMapper.readValue(IAM_PAYLOAD, AuthUserResponse.class);

    assertThat(response.getRefreshToken(), is("8f14e45f-ceea-467a-9ae4-9b3f4a1c2d3e"));
  }

  @Test
  void neverWritesTheRefreshTokenToTheBrowser() throws Exception {
    AuthUserResponse response = objectMapper.readValue(IAM_PAYLOAD, AuthUserResponse.class);

    JsonNode written = objectMapper.readTree(objectMapper.writeValueAsString(response));

    assertThat(written.has("refreshToken"), is(false));
    assertThat(written.get("accessToken").asText(), is("access-token"));
  }
}
