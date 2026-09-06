package com.lynq.bff.client.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * What lynq-ml derives from a resume on {@code POST /dmz/resume/skill-extraction}.
 *
 * <p>Only the similarity tags are mapped. The endpoint also returns the skills
 * bucketed into technical/tools/soft, but on the import path those already reach
 * the app-backend inside the parsed resume itself, so re-reading them here would
 * only give two sources for the same thing. The tags have no other origin.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class SkillExtractionResponse {

  @JsonProperty("similarity_tags")
  private List<String> similarityTags;
}
