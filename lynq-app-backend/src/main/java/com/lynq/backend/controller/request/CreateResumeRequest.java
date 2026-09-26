package com.lynq.backend.controller.request;

import com.lynq.backend.enums.Language;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class CreateResumeRequest {

  private String name;

  @NotNull
  private Language language;

  @NotNull
  private Object resume;

  @NotBlank
  private String fileId;

  private List<String> similarityTags;

}
