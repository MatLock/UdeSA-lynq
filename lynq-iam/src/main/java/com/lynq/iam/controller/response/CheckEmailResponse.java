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
@Schema(description = "Result of an email validity and availability check")
public class CheckEmailResponse {

  @Schema(description = "Whether the email has a valid format and is available", example = "true")
  private boolean valid;

  @Schema(description = "Reason the email is not valid; null when valid",
      example = "Email is already taken")
  private String reason;

}