package com.lynq.bff.service;

import com.lynq.bff.client.LynqBackendClient;
import com.lynq.bff.client.LynqFileStorageClient;
import com.lynq.bff.client.response.DeletedResumeResponse;
import com.lynq.bff.exceptions.BadGatewayException;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

/**
 * Deleting a resume the candidate created, which lives in two services: the row in
 * lynq-app-backend and the PDF in lynq-file-storage. Neither of them can see the other, so the
 * orchestration belongs here.
 *
 * <p>The order matters. The row goes first: it is what the candidate sees, it is the one that can
 * legitimately fail (a resume that is not theirs), and it is the only step worth reporting as an
 * error. Once it is gone the PDF is unreachable from the product, so a failure to drop the file
 * leaves an orphan in storage rather than a resume the user was told they deleted and still see.
 */
@Service
@Log4j2
public class ResumeDeletionService {

  private static final String DELETE_FAILED = "The resume could not be deleted";

  private final CandidateReader candidateReader;
  private final LynqBackendClient lynqBackendClient;
  private final LynqFileStorageClient lynqFileStorageClient;

  public ResumeDeletionService(CandidateReader candidateReader,
                               LynqBackendClient lynqBackendClient,
                               LynqFileStorageClient lynqFileStorageClient) {
    this.candidateReader = candidateReader;
    this.lynqBackendClient = lynqBackendClient;
    this.lynqFileStorageClient = lynqFileStorageClient;
  }

  public void delete(String resumeId, Caller caller) {
    candidateReader.read(caller);

    log.info("message= Started resume deletion, user_id={}, resume_id={}",
        caller.userId(), resumeId);

    DeletedResumeResponse deleted = deleteRow(resumeId, caller);
    deleteStoredPdf(deleted, caller);

    log.info("message= Finished resume deletion, user_id={}, resume_id={}",
        caller.userId(), resumeId);
  }

  private DeletedResumeResponse deleteRow(String resumeId, Caller caller) {
    try {
      return lynqBackendClient
          .deleteResume(resumeId, caller.requestUuid(), caller.authorization())
          .getData();
    } catch (RuntimeException e) {
      throw new BadGatewayException(DELETE_FAILED, e);
    }
  }

  /**
   * Best effort by design: the resume is already gone, so failing here would report an error for
   * something the candidate has, correctly, seen happen. The orphaned file is logged instead.
   */
  private void deleteStoredPdf(DeletedResumeResponse deleted, Caller caller) {
    if (deleted == null || deleted.getFileId() == null) {
      return;
    }

    try {
      lynqFileStorageClient.deleteFile(deleted.getFileId(), caller.requestUuid(), caller.userId());
    } catch (RuntimeException e) {
      log.warn("message= Deleted resume but its PDF could not be dropped, "
          + "user_id={}, resume_id={}, file_id={}",
          caller.userId(), deleted.getId(), deleted.getFileId(), e);
    }
  }
}
