package com.lynq.backend.controller.response;

import com.lynq.backend.enums.Language;
import java.time.LocalDate;
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
public class GetUserResumeRestResponse {

  private String id;
  private String name;
  private Language language;
  private LocalDate createdOn;
  private Object resume;
  private String pdfUrl;

}
