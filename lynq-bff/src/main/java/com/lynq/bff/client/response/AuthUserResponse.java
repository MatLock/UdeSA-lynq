package com.lynq.bff.client.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class AuthUserResponse {

  private String id;
  private String username;
  private String email;
  private OffsetDateTime creationDate;
  @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
  private String refreshToken;
  private String accessToken;
}
