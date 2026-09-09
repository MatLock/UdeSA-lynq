package com.lynq.iam.controller.request;

import com.lynq.iam.model.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Request body for creating a new user")
public class CreateUserRequest {

  @NotBlank
  @Size(min = 3, max = 20)
  @Schema(description = "Unique username", example = "johndoe", minLength = 3, maxLength = 20)
  private String username;

  @NotBlank
  @Size(min = 8)
  @Schema(description = "User password", example = "P@ssw0rd123", minLength = 8)
  private String password;

  @NotBlank
  @Email
  @Size(max = 100)
  @Schema(description = "Unique email address", example = "johndoe@example.com", maxLength = 100)
  private String email;

  @NotNull
  @Schema(description = "Role the user is registered with", example = "R_CANDIDATE")
  private Role role;

}
