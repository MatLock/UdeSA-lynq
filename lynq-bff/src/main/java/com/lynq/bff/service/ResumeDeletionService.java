package com.lynq.bff.service;

import com.lynq.bff.client.LynqBackendClient;
import com.lynq.bff.exceptions.BadGatewayException;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

/**
 * Deleting a resume the candidate created. Only lynq-app-backend is involved: the PDF in
 * lynq-file-storage is kept on purpose, because an application made with this resume must still
 * be able to download the document it was made with.
 */
@Service
@Log4j2
public class ResumeDeletionService {

  private static final String DELETE_FAILED = "The resume could not be deleted";

  private final LynqBackendClient lynqBackendClient;

  public ResumeDeletionService(LynqBackendClient lynqBackendClient) {
    this.lynqBackendClient = lynqBackendClient;
  }

  public void delete(String resumeId, Caller caller) {
    log.info("message= Started resume deletion, user_id={}, resume_id={}",
        caller.userId(), resumeId);

    try {
      lynqBackendClient.deleteResume(resumeId, caller.requestUuid(), caller.authorization());
    } catch (RuntimeException e) {
      throw new BadGatewayException(DELETE_FAILED, e);
    }

    log.info("message= Finished resume deletion, user_id={}, resume_id={}",
        caller.userId(), resumeId);
  }
}
