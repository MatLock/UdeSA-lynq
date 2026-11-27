package com.lynq.bff.service;

import com.lynq.bff.client.LynqBackendClient;
import com.lynq.bff.client.LynqFileStorageClient;
import com.lynq.bff.client.LynqLlmClient;
import com.lynq.bff.client.request.CreateResumeRequest;
import com.lynq.bff.client.request.LanguageDetectionRequest;
import com.lynq.bff.client.request.ParseResumeRequest;
import com.lynq.bff.client.response.LanguageDetectionResponse;
import com.lynq.bff.enums.Language;
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
  private final LynqLlmClient lynqLlmClient;

  public ResumeImportService(LynqBackendClient lynqBackendClient,
                             LynqFileStorageClient lynqFileStorageClient,
                             LynqLlmClient lynqLlmClient) {
    this.lynqBackendClient = lynqBackendClient;
    this.lynqFileStorageClient = lynqFileStorageClient;
    this.lynqLlmClient = lynqLlmClient;
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
      SkillExtractionResponse extracted = extractSkills(resume, fallbackLanguage, fileId, caller);
      Object completed = ParsedResume.withExtractedSkills(resume, extracted);
      Object stored = store(completed, language, similarityTagsOf(extracted), fileId, caller);

      log.info("message= Finished resume import, user_id={}, file_id={}", caller.userId(), fileId);

      return stored;
    } catch (RuntimeException e) {
      discardQuietly(fileId, caller);
      throw new BadGatewayException(IMPORT_FAILED, e);
    }
  }

  private String readUrl(String fileId, Caller caller) {
    return lynqFileStorageClient.createDownloadUrl(fileId, caller.requestUuid(),
          caller.authorization())
        .getData()
        .getDownloadUrl();
  }

  private Object parse(String documentUrl, Caller caller) {
    ParseResumeRequest request = ParseResumeRequest.builder()
        .preSignedUrl(documentUrl)
        .build();

    return lynqLlmClient.parseResume(request, caller.requestUuid(), caller.authorization()).getData();
  }

  private String language(Object resume, String fallbackLanguage, Caller caller) {
    String fallback = stored(fallbackLanguage);
    String prose = ParsedResume.prose(resume);
    if (prose.isBlank()) {
      return fallback;
    }

    LanguageDetectionRequest request = LanguageDetectionRequest.builder().text(prose).build();
    LanguageDetectionResponse detected = lynqLlmClient
        .detectLanguage(request, caller.requestUuid(), caller.authorization())
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
   * The skills and transferable capabilities lynq-llm reads in the parsed resume, best-effort.
   *
   * <p>Parsing only recovers the skills the candidate listed, and it can miss them
   * entirely: a resume that groups its skills under headings ("Cloud / DevOps:
   * Terraform, Azure") or comes from a non-software field may parse with few or
   * no technical skills even though its experience is full of them. The wizard
   * asks lynq-llm for the skills the resume implies and merges them into the
   * buckets; this path does the same, so an imported resume and a typed one
   * reach the candidate's profile with the same vocabulary. The generalized tags
   * — "Asynchronous Messaging" rather than Kafka or RabbitMQ — come from the
   * same call and have no other source.
   *
   * <p>This is a second LLM round-trip on a path that already made one, so a
   * failure here degrades to the parsed resume without extra skills or tags
   * instead of losing the import: both can be regenerated from the stored
   * resume later, the uploaded document cannot.
   *
   * <p>{@code language} is the caller's UI language, the one the wizard passes:
   * it decides the wording of the soft skills. Technical skills and tools keep
   * the resume's wording, and the tags are always English.
   *
   * @return the extraction, or {@code null} when lynq-llm could not produce one
   */
  private SkillExtractionResponse extractSkills(Object resume, String language, String fileId,
                                                Caller caller) {
    try {
      return lynqLlmClient
          .extractResumeSkills(resume, language, caller.requestUuid(), caller.authorization())
          .getData();
    } catch (RuntimeException e) {
      log.warn("message= Could not extract the skills of an imported resume, it is stored with "
          + "only its parsed skills and no tags, user_id={}, file_id={}", caller.userId(), fileId, e);
      return null;
    }
  }

  private static List<String> similarityTagsOf(SkillExtractionResponse extracted) {
    return extracted == null || extracted.getSimilarityTags() == null
        ? List.of()
        : extracted.getSimilarityTags();
  }

  private Object store(Object resume, String language, List<String> similarityTags, String fileId,
                       Caller caller) {
    CreateResumeRequest request = CreateResumeRequest.builder()
        .name(ParsedResume.fullName(resume))
        .language(Language.valueOf(language))
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
      lynqFileStorageClient.deleteFile(fileId, caller.requestUuid(), caller.authorization());
    } catch (RuntimeException e) {
      log.warn("message= Could not roll back a failed resume import, user_id={}, file_id={}",
          caller.userId(), fileId, e);
    }
  }
}
