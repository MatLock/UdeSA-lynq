package com.lynq.backend.controller.response;

import com.lynq.backend.enums.UserType;
import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class GetUserRestResponse {

  private String id;
  private UserType userType;
  private String fullName;
  private String userProfileImageUrl;
  private String currentPosition;
  private String about;
  private String githubUrl;
  private String linkedinUrl;
  private LocalDate birthDate;
  private LocalDate createdOn;

}
