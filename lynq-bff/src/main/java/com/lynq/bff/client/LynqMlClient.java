package com.lynq.bff.client;

import com.lynq.bff.client.request.LanguageDetectionRequest;
import com.lynq.bff.client.request.ParseResumeRequest;
import com.lynq.bff.client.request.ResumeTemplateCreationRequest;
import com.lynq.bff.client.request.TranslateResumeRequest;
import com.lynq.bff.client.response.LanguageDetectionResponse;
import com.lynq.bff.client.response.SkillExtractionResponse;
import com.lynq.bff.controller.response.GlobalRestResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "lynqMl", url = "${lynq.ml.url}")
public interface LynqMlClient {

  String REQUEST_UUID_HEADER = "lynq-request-uuid";
  String USER_ID_HEADER = "user-id";

  @PostMapping("/dmz/resume-template-creation")
  void createResumeTemplate(
      @RequestBody ResumeTemplateCreationRequest request,
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid,
      @RequestHeader(USER_ID_HEADER) String userId);

  @PostMapping("/dmz/parse-resume")
  GlobalRestResponse<Object> parseResume(
      @RequestBody ParseResumeRequest request,
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid,
      @RequestHeader(USER_ID_HEADER) String userId);

  @PostMapping("/dmz/translate")
  GlobalRestResponse<Object> translateResume(
      @RequestBody TranslateResumeRequest request,
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid,
      @RequestHeader(USER_ID_HEADER) String userId);

  @PostMapping("/dmz/detect-language")
  GlobalRestResponse<LanguageDetectionResponse> detectLanguage(
      @RequestBody LanguageDetectionRequest request,
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid,
      @RequestHeader(USER_ID_HEADER) String userId);

  /**
   * Derive the transferable capabilities behind a resume's skills.
   *
   * <p>The body is the parsed resume as lynq-ml itself returned it, so it is
   * relayed as an opaque object rather than re-modelled here. {@code language}
   * only governs the soft-skill wording; the similarity tags always come back in
   * English so a resume in one language stays matchable against postings in
   * another.
   */
  @PostMapping("/dmz/resume/skill-extraction")
  GlobalRestResponse<SkillExtractionResponse> extractResumeSkills(
      @RequestBody Object resume,
      @RequestParam("language") String language,
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid,
      @RequestHeader(USER_ID_HEADER) String userId);
}
