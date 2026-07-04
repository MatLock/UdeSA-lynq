package com.lynq.iam.controller.response;

import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "Result of a username validity and availability check")
public class CheckUsernameResponse {

  @Schema(description = "Whether the username has a valid format and is available", example = "true")
  private boolean valid;

  @Schema(description = "Reason the username is not valid; null when valid",
      example = "Username is already taken")
  private String reason;

}