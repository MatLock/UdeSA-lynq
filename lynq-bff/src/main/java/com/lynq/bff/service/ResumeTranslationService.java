package com.lynq.bff.service;

import com.lynq.bff.client.LynqBackendClient;
import com.lynq.bff.client.LynqMlClient;
import com.lynq.bff.client.request.TranslateResumeRequest;
import com.lynq.bff.client.response.SupportedLanguageResponse;
import com.lynq.bff.client.response.UserResumeResponse;
import com.lynq.bff.exceptions.BadGatewayException;
import com.lynq.bff.exceptions.BadRequestException;
import java.util.List;
import java.util.Locale;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

/**
 * Translates one of the candidate's stored resumes into another language and returns the
 * translated JSON — nothing more. Rendering the PDF and storing the resume deliberately stay out:
 * the candidate first looks at the translation, then picks a template and previews it through
 * POST /resume/preview, and only a confirmation stores it through lynq-app-backend's
 * POST /user/resume. The target language must be one lynq-app-backend supports (its
 * supported_languages table) and one the candidate does not already hold a resume in — the same
 * resume twice in the same language would be indistinguishable in every list.
 */
@Service
@Log4j2
public class ResumeTranslationService {

  private static final String LANGUAGE_REQUIRED = "A target language is required";
  private static final String RESUMES_UNREADABLE = "The caller's resumes could not be read";
  private static final String LANGUAGES_UNREADABLE = "The supported languages could not be read";
  private static final String RESUME_NOT_FOUND = "Resume '%s' not found";
  private static final String LANGUAGE_NOT_SUPPORTED = "Language '%s' is not supported";
  private static final String LANGUAGE_TAKEN = "A resume in language '%s' already exists";
  private static final String TRANSLATE_FAILED = "The resume could not be translated";

  private final CandidateReader candidateReader;
  private final LynqBackendClient lynqBackendClient;
  private final LynqMlClient lynqMlClient;

  public ResumeTranslationService(CandidateReader candidateReader,
                                  LynqBackendClient lynqBackendClient,
                                  LynqMlClient lynqMlClient) {
    this.candidateReader = candidateReader;
    this.lynqBackendClient = lynqBackendClient;
    this.lynqMlClient = lynqMlClient;
  }

  public Object translate(String resumeId, String targetLanguage, Caller caller) {
    if (targetLanguage == null || targetLanguage.isBlank()) {
      throw new BadRequestException(LANGUAGE_REQUIRED);
    }

    candidateReader.read(caller);
    String language = targetLanguage.trim().toUpperCase(Locale.ROOT);

    List<UserResumeResponse> resumes = readResumes(caller);
    UserResumeResponse source = sourceOf(resumes, resumeId);
    validateTarget(language, resumes, caller);

    log.info("message= Started resume translation, user_id={}, resume_id={}, language={}",
        caller.userId(), resumeId, language);

    Object translated = translateContent(source, language, caller);

    log.info("message= Finished resume translation, user_id={}, resume_id={}, language={}",
        caller.userId(), resumeId, language);

    return translated;
  }

  private List<UserResumeResponse> readResumes(Caller caller) {
    try {
      List<UserResumeResponse> resumes = lynqBackendClient
          .getUserResumes(caller.requestUuid(), caller.authorization())
          .getData();
      return resumes == null ? List.of() : resumes;
    } catch (RuntimeException e) {
      throw new BadGatewayException(RESUMES_UNREADABLE, e);
    }
  }

  private UserResumeResponse sourceOf(List<UserResumeResponse> resumes, String resumeId) {
    return resumes.stream()
        .filter(resume -> resumeId.equals(resume.getId()))
        .findFirst()
        .orElseThrow(() -> new BadRequestException(String.format(RESUME_NOT_FOUND, resumeId)));
  }

  /**
   * The target must be a language the backend's supported_languages table offers, and one the
   * candidate holds no resume in yet — which also rules out translating a resume into its own
   * language.
   */
  private void validateTarget(String language, List<UserResumeResponse> resumes, Caller caller) {
    List<SupportedLanguageResponse> supported;
    try {
      supported = lynqBackendClient
          .getSupportedResumeLanguages(caller.requestUuid(), caller.authorization())
          .getData();
    } catch (RuntimeException e) {
      throw new BadGatewayException(LANGUAGES_UNREADABLE, e);
    }

    boolean known = supported != null && supported.stream()
        .anyMatch(entry -> language.equalsIgnoreCase(entry.getCode()));
    if (!known) {
      throw new BadRequestException(String.format(LANGUAGE_NOT_SUPPORTED, language));
    }

    boolean taken = resumes.stream()
        .anyMatch(resume -> language.equalsIgnoreCase(resume.getLanguage()));
    if (taken) {
      throw new BadRequestException(String.format(LANGUAGE_TAKEN, language));
    }
  }

  private Object translateContent(UserResumeResponse source, String language, Caller caller) {
    TranslateResumeRequest request = TranslateResumeRequest.builder()
        .resume(source.getResume())
        .language(language)
        .build();

    try {
      return lynqMlClient
          .translateResume(request, caller.requestUuid(), caller.userId())
          .getData();
    } catch (RuntimeException e) {
      throw new BadGatewayException(TRANSLATE_FAILED, e);
    }
  }
}
