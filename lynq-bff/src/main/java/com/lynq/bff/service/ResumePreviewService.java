package com.lynq.bff.service;

import com.lynq.bff.client.LynqFileStorageClient;
import com.lynq.bff.client.LynqMlClient;
import com.lynq.bff.client.request.CreateFileUploadRequest;
import com.lynq.bff.client.request.ResumeTemplateCreationRequest;
import com.lynq.bff.client.response.CreateFileUploadResponse;
import com.lynq.bff.client.response.UserResponse;
import com.lynq.bff.controller.request.PreviewResumeRequest;
import com.lynq.bff.controller.response.ResumePreviewRestResponse;
import com.lynq.bff.exceptions.BadGatewayException;
import com.lynq.bff.exceptions.BadRequestException;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

@Service
@Log4j2
public class ResumePreviewService {

  private static final String RESUME_FILE_NAME = "resume.pdf";
  private static final String PDF_CONTENT_TYPE = "application/pdf";

  private static final String RESUME_REQUIRED = "A resume is required to render a preview";
  private static final String TEMPLATE_REQUIRED = "A template is required to render a preview";
  private static final String RENDER_FAILED = "The resume PDF could not be rendered and stored";
  private static final String READ_URL_FAILED = "The stored resume PDF could not be signed to read";
  private static final String REGISTER_FAILED = "The resume PDF could not be registered for upload";
  private static final String DISCARD_FAILED = "The previewed resume PDF could not be discarded";

  private final CandidateReader candidateReader;
  private final LynqFileStorageClient lynqFileStorageClient;
  private final LynqMlClient lynqMlClient;

  public ResumePreviewService(CandidateReader candidateReader,
                              LynqFileStorageClient lynqFileStorageClient,
                              LynqMlClient lynqMlClient) {
    this.candidateReader = candidateReader;
    this.lynqFileStorageClient = lynqFileStorageClient;
    this.lynqMlClient = lynqMlClient;
  }

  public ResumePreviewRestResponse preview(PreviewResumeRequest request, Caller caller) {
    if (request.getResume() == null) {
      throw new BadRequestException(RESUME_REQUIRED);
    }
    if (request.getTemplate() == null) {
      throw new BadRequestException(TEMPLATE_REQUIRED);
    }

    UserResponse user = candidateReader.read(caller);
    CreateFileUploadResponse upload = registerPdf(caller);

    log.info("message= Started resume preview, user_id={}, template={}, file_id={}",
        caller.userId(), request.getTemplate(), upload.getFileId());

    try {
      render(request, user, upload, caller);
      lynqFileStorageClient.confirmUpload(upload.getFileId(), caller.requestUuid(), caller.userId());
    } catch (RuntimeException e) {
      discardQuietly(upload.getFileId(), caller);
      throw new BadGatewayException(RENDER_FAILED, e);
    }

    ResumePreviewRestResponse preview = ResumePreviewRestResponse.builder()
        .fileId(upload.getFileId())
        .pdfUrl(readUrl(upload.getFileId(), caller))
        .build();

    log.info("message= Finished resume preview, user_id={}, file_id={}",
        caller.userId(), preview.getFileId());

    return preview;
  }

  public void discard(String fileId, Caller caller) {
    try {
      lynqFileStorageClient.deleteFile(fileId, caller.requestUuid(), caller.userId());
    } catch (RuntimeException e) {
      throw new BadGatewayException(DISCARD_FAILED, e);
    }

    log.info("message= Discarded resume preview, user_id={}, file_id={}", caller.userId(), fileId);
  }

  private CreateFileUploadResponse registerPdf(Caller caller) {
    CreateFileUploadRequest request = CreateFileUploadRequest.builder()
        .fileName(RESUME_FILE_NAME)
        .contentType(PDF_CONTENT_TYPE)
        .build();

    try {
      return lynqFileStorageClient
          .createUpload(request, caller.requestUuid(), caller.userId())
          .getData();
    } catch (RuntimeException e) {
      throw new BadGatewayException(REGISTER_FAILED, e);
    }
  }

  private void render(PreviewResumeRequest request, UserResponse user,
                      CreateFileUploadResponse upload, Caller caller) {
    ResumeTemplateCreationRequest renderRequest = ResumeTemplateCreationRequest.builder()
        .resumeContent(request.getResume())

        .profileUrl(user.getUserProfileImageUrl())
        .putResumeUrl(upload.getUploadUrl())
        .template(request.getTemplate())
        .build();

    lynqMlClient.createResumeTemplate(renderRequest, caller.requestUuid(), caller.userId());
  }

  private String readUrl(String fileId, Caller caller) {
    try {
      return lynqFileStorageClient.createDownloadUrl(fileId, caller.requestUuid())
          .getData()
          .getDownloadUrl();
    } catch (RuntimeException e) {
      throw new BadGatewayException(READ_URL_FAILED, e);
    }
  }

  private void discardQuietly(String fileId, Caller caller) {
    try {
      lynqFileStorageClient.deleteFile(fileId, caller.requestUuid(), caller.userId());
    } catch (RuntimeException e) {
      log.warn("message= Could not roll back a failed resume preview, user_id={}, file_id={}",
          caller.userId(), fileId, e);
    }
  }
}
