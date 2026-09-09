package com.lynq.bff.service;

import com.lynq.bff.client.LynqBackendClient;
import com.lynq.bff.client.LynqFileStorageClient;
import com.lynq.bff.client.LynqMlClient;
import com.lynq.bff.client.request.CreateResumeRequest;
import com.lynq.bff.client.request.LanguageDetectionRequest;
import com.lynq.bff.client.request.ParseResumeRequest;
import com.lynq.bff.client.response.LanguageDetectionResponse;
import com.lynq.bff.client.response.SkillExtractionResponse;
import com.lynq.bff.exceptions.BadGatewayException;
import java.util.List;
import java.util.Locale;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

@Service
@Log4j2
public class ResumeImportService {

  private static final List<String> LANGUAGES = List.of("EN", "ES", "FR", "PR");
  private static final String DEFAULT_LANGUAGE = "EN";

  private static final String IMPORT_FAILED = "The uploaded resume could not be imported";
  private static final String CONFIRM_FAILED = "The uploaded resume document could not be confirmed";

  private final LynqBackendClient lynqBackendClient;
  private final LynqFileStorageClient lynqFileStorageClient;
  private final LynqMlClient lynqMlClient;

  public ResumeImportService(LynqBackendClient lynqBackendClient,
                             LynqFileStorageClient lynqFileStorageClient,
                             LynqMlClient lynqMlClient) {
    this.lynqBackendClient = lynqBackendClient;
    this.lynqFileStorageClient = lynqFileStorageClient;
    this.lynqMlClient = lynqMlClient;
  }

  public Object importUploadedDocument(String fileId, String fallbackLanguage, Caller caller) {
    log.info("message= Started resume import, user_id={}, file_id={}", caller.userId(), fileId);

    try {
      lynqBackendClient.confirmResumeUpload(fileId, caller.requestUuid(), caller.authorization());
    } catch (RuntimeException e) {
      throw new BadGatewayException(CONFIRM_FAILED, e);
    }

    try {
      Object resume = parse(readUrl(fileId, caller), caller);
      String language = language(resume, fallbackLanguage, caller);
      List<String> similarityTags = similarityTags(resume, fallbackLanguage, fileId, caller);
      Object stored = store(resume, language, similarityTags, fileId, caller);

      log.info("message= Finished resume import, user_id={}, file_id={}", caller.userId(), fileId);

      return stored;
    } catch (RuntimeException e) {
      discardQuietly(fileId, caller);
      throw new BadGatewayException(IMPORT_FAILED, e);
    }
  }

  private String readUrl(String fileId, Caller caller) {
    return lynqFileStorageClient.createDownloadUrl(fileId, caller.requestUuid())
        .getData()
        .getDownloadUrl();
  }

  private Object parse(String documentUrl, Caller caller) {
    ParseResumeRequest request = ParseResumeRequest.builder()
        .preSignedUrl(documentUrl)
        .build();

    return lynqMlClient.parseResume(request, caller.requestUuid(), caller.userId()).getData();
  }

  private String language(Object resume, String fallbackLanguage, Caller caller) {
    String fallback = stored(fallbackLanguage);
    String prose = ParsedResume.prose(resume);
    if (prose.isBlank()) {
      return fallback;
    }

    LanguageDetectionRequest request = LanguageDetectionRequest.builder().text(prose).build();
    LanguageDetectionResponse detected = lynqMlClient
        .detectLanguage(request, caller.requestUuid(), caller.userId())
        .getData();

    return detected == null ? fallback : stored(detected.getLanguage());
  }

  private static String stored(String language) {
    if (language == null) {
      return DEFAULT_LANGUAGE;
    }
    String code = language.trim().toUpperCase(Locale.ROOT);
    return LANGUAGES.contains(code) ? code : DEFAULT_LANGUAGE;
  }

  /**
   * The transferable capabilities behind the resume's skills, best-effort.
   *
   * <p>Parsing only recovers the skills the candidate literally wrote down. The
   * generalized tags — "Asynchronous Messaging" rather than Kafka or RabbitMQ —
   * have no other source, and without them an imported resume can only be
   * matched on exact skill names, scoring below the same resume typed into the
   * wizard, which asks lynq-ml for them explicitly.
   *
   * <p>This is a second LLM round-trip on a path that already made one, so a
   * failure here degrades to no tags instead of losing the import: the tags can
   * be regenerated from the stored resume later, the uploaded document cannot.
   *
   * <p>{@code language} is the caller's UI language, passed for parity with the
   * wizard. It only decides the wording of the soft skills, which this path does
   * not read — the tags themselves are always English.
   */
  private List<String> similarityTags(Object resume, String language, String fileId,
                                      Caller caller) {
    try {
      SkillExtractionResponse extracted = lynqMlClient
          .extractResumeSkills(resume, language, caller.requestUuid(), caller.userId())
          .getData();

      return extracted == null || extracted.getSimilarityTags() == null
          ? List.of()
          : extracted.getSimilarityTags();
    } catch (RuntimeException e) {
      log.warn("message= Could not derive the similarity tags of an imported resume, it is stored "
          + "without them, user_id={}, file_id={}", caller.userId(), fileId, e);
      return List.of();
    }
  }

  private Object store(Object resume, String language, List<String> similarityTags, String fileId,
                       Caller caller) {
    CreateResumeRequest request = CreateResumeRequest.builder()
        .name(ParsedResume.fullName(resume))
        .language(language)
        .resume(resume)
        .similarityTags(similarityTags)
        .fileId(fileId)
        .build();

    return lynqBackendClient
        .createResume(request, caller.requestUuid(), caller.authorization())
        .getData();
  }

  private void discardQuietly(String fileId, Caller caller) {
    try {
      lynqFileStorageClient.deleteFile(fileId, caller.requestUuid(), caller.userId());
    } catch (RuntimeException e) {
      log.warn("message= Could not roll back a failed resume import, user_id={}, file_id={}",
          caller.userId(), fileId, e);
    }
  }
}
