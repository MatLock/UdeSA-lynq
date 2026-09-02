package com.lynq.bff.service;

import com.lynq.bff.client.LynqBackendClient;
import com.lynq.bff.client.request.UpdateResumeAliasRequest;
import com.lynq.bff.exceptions.BadGatewayException;
import com.lynq.bff.exceptions.BadRequestException;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

/**
 * Assigning (or replacing — same operation) the alias a candidate uses to tell one of their
 * resumes apart from the others. The alias lives in a single service, lynq-app-backend, so the
 * gateway only validates the input, checks the caller is a candidate, and relays; ownership of
 * the resume is enforced downstream, where the row lives.
 */
@Service
@Log4j2
public class ResumeAliasService {

  private static final int MAX_ALIAS_LENGTH = 100;

  private static final String ALIAS_REQUIRED = "An alias is required";
  private static final String ALIAS_TOO_LONG =
      "The alias cannot be longer than " + MAX_ALIAS_LENGTH + " characters";
  private static final String ALIAS_NOT_SAVED = "The resume alias could not be saved";

  private final CandidateReader candidateReader;
  private final LynqBackendClient lynqBackendClient;

  public ResumeAliasService(CandidateReader candidateReader, LynqBackendClient lynqBackendClient) {
    this.candidateReader = candidateReader;
    this.lynqBackendClient = lynqBackendClient;
  }

  public Object assign(String resumeId, String alias, Caller caller) {
    String trimmed = alias == null ? "" : alias.trim();
    if (trimmed.isEmpty()) {
      throw new BadRequestException(ALIAS_REQUIRED);
    }
    if (trimmed.length() > MAX_ALIAS_LENGTH) {
      throw new BadRequestException(ALIAS_TOO_LONG);
    }

    candidateReader.read(caller);

    log.info("message= Assigning resume alias, user_id={}, resume_id={}",
        caller.userId(), resumeId);

    UpdateResumeAliasRequest request = UpdateResumeAliasRequest.builder()
        .alias(trimmed)
        .build();

    try {
      return lynqBackendClient
          .updateResumeAlias(resumeId, request, caller.requestUuid(), caller.authorization())
          .getData();
    } catch (RuntimeException e) {
      throw new BadGatewayException(ALIAS_NOT_SAVED, e);
    }
  }
}
