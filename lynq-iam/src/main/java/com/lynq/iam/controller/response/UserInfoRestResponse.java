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
@Schema(description = "User information extracted from the access token")
public class UserInfoRestResponse {

  @Schema(description = "User ID", example = "550e8400-e29b-41d4-a716-446655440000")
  private String id;
  @Schema(description = "Username", example = "johndoe")
  private String username;
  @Schema(description = "Email address", example = "johndoe@example.com")
  private String email;

}
