package com.lynq.bff.controller.impl;

import com.lynq.bff.controller.ResumeController;
import com.lynq.bff.controller.request.PreviewResumeRequest;
import com.lynq.bff.controller.response.GlobalRestResponse;
import com.lynq.bff.controller.response.ResumePreviewRestResponse;
import com.lynq.bff.filter.JwtSignatureFilter;
import com.lynq.bff.service.Caller;
import com.lynq.bff.service.ResumeDeletionService;
import com.lynq.bff.service.ResumeImportService;
import com.lynq.bff.service.ResumePreviewService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/resume")
public class ResumeControllerImpl implements ResumeController {

  private static final String REQUEST_UUID_HEADER = "lynq-request-uuid";
  private static final String AUTHORIZATION_HEADER = "Authorization";

  private final ResumePreviewService resumePreviewService;
  private final ResumeImportService resumeImportService;
  private final ResumeDeletionService resumeDeletionService;

  public ResumeControllerImpl(ResumePreviewService resumePreviewService,
                              ResumeImportService resumeImportService,
                              ResumeDeletionService resumeDeletionService) {
    this.resumePreviewService = resumePreviewService;
    this.resumeImportService = resumeImportService;
    this.resumeDeletionService = resumeDeletionService;
  }

  @Override
  @PostMapping("/preview")
  public ResponseEntity<GlobalRestResponse<ResumePreviewRestResponse>> previewResume(
      @RequestBody PreviewResumeRequest request,
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid,
      @RequestHeader(AUTHORIZATION_HEADER) String authorization,
      @RequestAttribute(JwtSignatureFilter.VERIFIED_USER_ID) String userId) {
    ResumePreviewRestResponse preview =
        resumePreviewService.preview(request, new Caller(userId, requestUuid, authorization));

    return ResponseEntity
        .status(HttpStatus.CREATED)
        .body(new GlobalRestResponse<>(true, preview));
  }

  @Override
  @PostMapping("/document/{fileId}/import")
  public ResponseEntity<GlobalRestResponse<Object>> importResumeDocument(
      @PathVariable String fileId,
      @RequestParam(defaultValue = "EN") String language,
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid,
      @RequestHeader(AUTHORIZATION_HEADER) String authorization,
      @RequestAttribute(JwtSignatureFilter.VERIFIED_USER_ID) String userId) {
    Object resume = resumeImportService.importUploadedDocument(
        fileId, language, new Caller(userId, requestUuid, authorization));

    return ResponseEntity
        .status(HttpStatus.CREATED)
        .body(new GlobalRestResponse<>(true, resume));
  }

  @Override
  @DeleteMapping("/preview/{fileId}")
  public ResponseEntity<Void> discardResumePreview(
      @PathVariable String fileId,
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid,
      @RequestAttribute(JwtSignatureFilter.VERIFIED_USER_ID) String userId) {
    resumePreviewService.discard(fileId, new Caller(userId, requestUuid, null));

    return ResponseEntity.noContent().build();
  }

  @Override
  @DeleteMapping("/{resumeId}")
  public ResponseEntity<Void> deleteResume(
      @PathVariable String resumeId,
      @RequestHeader(REQUEST_UUID_HEADER) String requestUuid,
      @RequestHeader(AUTHORIZATION_HEADER) String authorization,
      @RequestAttribute(JwtSignatureFilter.VERIFIED_USER_ID) String userId) {
    resumeDeletionService.delete(resumeId, new Caller(userId, requestUuid, authorization));

    return ResponseEntity.noContent().build();
  }
}
