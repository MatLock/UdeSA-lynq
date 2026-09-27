package com.lynq.bff.service;

import com.lynq.bff.client.LynqMlClient;
import com.lynq.bff.client.request.LanguageDetectionRequest;
import com.lynq.bff.client.request.SkillEnhanceRequest;
import com.lynq.bff.client.request.TranslateResumeRequest;
import com.lynq.bff.client.response.LanguageDetectionResponse;
import com.lynq.bff.client.response.SkillEnhanceResponse;
import com.lynq.bff.client.response.SkillExtractionResponse;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

@Service
@Log4j2
public class MlService {

  private static final String SKILLS_NOT_ENHANCED = "The job post skills could not be extracted";
  private static final String TRANSLATE_FAILED = "The resume could not be translated";
  private static final String LANGUAGE_NOT_DETECTED = "The language could not be detected";
  private static final String SKILLS_NOT_EXTRACTED = "The resume skills could not be extracted";

  private final LynqMlClient lynqMlClient;

  public MlService(LynqMlClient lynqMlClient) {
    this.lynqMlClient = lynqMlClient;
  }

  public SkillEnhanceResponse enhanceSkills(SkillEnhanceRequest request, Caller caller) {
    log.info("message= Enhancing job post skills, user_id={}", caller.userId());

    return DownstreamErrors.call(
        () -> lynqMlClient
            .enhanceSkills(request, caller.requestUuid(), caller.userId())
            .getData(),
        SKILLS_NOT_ENHANCED);
  }

  public Object translateResume(TranslateResumeRequest request, Caller caller) {
    log.info("message= Translating a resume, user_id={}, language={}",
        caller.userId(), request.getLanguage());

    return DownstreamErrors.call(
        () -> lynqMlClient
            .translateResume(request, caller.requestUuid(), caller.userId())
            .getData(),
        TRANSLATE_FAILED);
  }

  public LanguageDetectionResponse detectLanguage(LanguageDetectionRequest request, Caller caller) {
    return DownstreamErrors.call(
        () -> lynqMlClient
            .detectLanguage(request, caller.requestUuid(), caller.userId())
            .getData(),
        LANGUAGE_NOT_DETECTED);
  }

  public SkillExtractionResponse extractResumeSkills(Object resume, String language,
                                                     Caller caller) {
    return DownstreamErrors.call(
        () -> lynqMlClient
            .extractResumeSkills(resume, language, caller.requestUuid(), caller.userId())
            .getData(),
        SKILLS_NOT_EXTRACTED);
  }
}
