package com.lynq.backend.controller.response;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class GetUserProfileRestResponse {

  private String fullName;
  private String profileImageUrl;
  private String currentPosition;
  private String about;
  private String githubUrl;
  private String linkedinUrl;
  private UserProfileCompanyRestResponse company;
  private List<UserProfileJobRestResponse> jobs;

}