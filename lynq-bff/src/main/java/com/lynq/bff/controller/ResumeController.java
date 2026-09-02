package com.lynq.bff.controller;

import com.lynq.bff.controller.request.PreviewResumeRequest;
import com.lynq.bff.controller.response.GlobalRestResponse;
import com.lynq.bff.controller.response.ResumePreviewRestResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "Resume", description = "Resume flows the gateway composes from several services")
public interface ResumeController {

  @Operation(
      summary = "Render a preview of a resume being created",
      description = "Turns a resume draft into a PDF the candidate can look at before it becomes "
          + "one of their resumes. The gateway drives the whole flow: it reads the caller from "
          + "lynq-app-backend (for the profile picture the template draws), registers the "
          + "destination file with lynq-file-storage, has lynq-ml render the template and upload "
          + "the PDF to the pre-signed URL, then marks the file available and signs a read URL for "
          + "it. Nothing is stored as a resume: the returned fileId is either sent to "
          + "lynq-app-backend's POST /user/resume to keep the document, or back to "
          + "DELETE /resume/preview/{fileId} to throw it away. A registered file that never gets "
          + "its PDF is deleted before the failure is reported.")
  @ApiResponses({
      @ApiResponse(
          responseCode = "201",
          description = "Preview rendered and stored",
          content = @Content(
              schema = @Schema(implementation = ResumePreviewRestResponse.class),
              examples = @ExampleObject(
                  name = "Preview",
                  value = """
                      {
                        "success": true,
                        "data": {
                          "fileId": "0195f2c1-3b1a-7c2d-9f31-3f6a5f2c9d41",
                          "pdfUrl": "https://lynq-bucket.s3.amazonaws.com/lynq/0195f2c1-3b1a-7c2d-9f31-3f6a5f2c9d41/resume.pdf?X-Amz-Signature=..."
                        }
                      }"""))),
      @ApiResponse(responseCode = "400", description = "The resume or the template is missing."),
      @ApiResponse(responseCode = "401", description = "The Authorization header is missing, or the "
          + "access token's signature is invalid or expired."),
      @ApiResponse(responseCode = "403", description = "The lynq-request-uuid header is missing, or "
          + "the caller is not a CANDIDATE."),
      @ApiResponse(responseCode = "502", description = "A service the flow depends on failed; "
          + "nothing is left behind.")
  })
  ResponseEntity<GlobalRestResponse<ResumePreviewRestResponse>> previewResume(
      PreviewResumeRequest request,
      @Parameter(hidden = true) String requestUuid,
      @Parameter(hidden = true) String authorization,
      @Parameter(hidden = true) String userId);

  @Operation(
      summary = "Import a resume document the candidate uploaded",
      description = "Turns a document already uploaded to the bucket into one of the candidate's "
          + "resumes. The browser gets a pre-signed upload URL from lynq-app-backend's "
          + "GET /user/generate-upload-resume and PUTs the file straight to storage — bytes never "
          + "cross our services — then calls this with the fileId it was given. The gateway then "
          + "confirms the document, has lynq-file-storage sign a read URL for it, has lynq-ml read "
          + "it into resume JSON and classify which language it is written in, and finally stores "
          + "the resume against the candidate. If any of that fails after the document has been "
          + "confirmed, the document is deleted before the failure is reported. The `language` "
          + "parameter is only a fallback, used when the parsed resume carries no prose to "
          + "classify.")
  @ApiResponses({
      @ApiResponse(
          responseCode = "201",
          description = "Resume imported and stored",
          content = @Content(
              examples = @ExampleObject(
                  name = "Imported resume",
                  value = """
                      {
                        "success": true,
                        "data": {
                          "id": "018f9c3a-2b1d-7c4e-9a6f-1e2d3c4b5a60",
                          "name": "Jane Doe",
                          "language": "EN",
                          "createdOn": "2026-09-02",
                          "resume": { "personal_info": { "full_name": "Jane Doe" } },
                          "pdfUrl": "https://lynq-bucket.s3.amazonaws.com/lynq/0195f2c1-3b1a-7c2d-9f31-3f6a5f2c9d41/resume.pdf?X-Amz-Signature=..."
                        }
                      }"""))),
      @ApiResponse(responseCode = "401", description = "The Authorization header is missing, or the "
          + "access token's signature is invalid or expired."),
      @ApiResponse(responseCode = "403", description = "The lynq-request-uuid header is missing, or "
          + "the caller is not a CANDIDATE."),
      @ApiResponse(responseCode = "502", description = "A service the flow depends on failed; the "
          + "uploaded document is not left behind as an orphan.")
  })
  ResponseEntity<GlobalRestResponse<Object>> importResumeDocument(
      @Parameter(
          name = "fileId",
          in = ParameterIn.PATH,
          required = true,
          description = "Id of the uploaded document, as returned by "
              + "GET /user/generate-upload-resume.",
          example = "0195f2c1-3b1a-7c2d-9f31-3f6a5f2c9d41")
      String fileId,
      @Parameter(
          name = "language",
          in = ParameterIn.QUERY,
          description = "The caller's UI language, used only if the resume has no prose to "
              + "classify.",
          example = "es")
      String language,
      @Parameter(hidden = true) String requestUuid,
      @Parameter(hidden = true) String authorization,
      @Parameter(hidden = true) String userId);

  @Operation(
      summary = "Discard a previewed resume PDF",
      description = "Deletes a PDF this gateway rendered that the candidate did not accept — they "
          + "went back to change the resume, or left the creation flow. lynq-file-storage only "
          + "lets the user who registered the file delete it, so a candidate can never reach "
          + "another one's document.")
  @ApiResponses({
      @ApiResponse(responseCode = "204", description = "Preview discarded."),
      @ApiResponse(responseCode = "401", description = "The Authorization header is missing, or the "
          + "access token's signature is invalid or expired."),
      @ApiResponse(responseCode = "403", description = "The lynq-request-uuid header is missing, or "
          + "the file belongs to another user."),
      @ApiResponse(responseCode = "502", description = "lynq-file-storage could not be reached.")
  })
  ResponseEntity<Void> discardResumePreview(
      @Parameter(
          name = "fileId",
          in = ParameterIn.PATH,
          required = true,
          description = "Id of the previewed PDF, as returned by POST /resume/preview.",
          example = "0195f2c1-3b1a-7c2d-9f31-3f6a5f2c9d41")
      String fileId,
      @Parameter(hidden = true) String requestUuid,
      @Parameter(hidden = true) String userId);

  @Operation(
      summary = "Delete a resume the candidate created",
      description = "Removes a resume the candidate had already saved. It lives in two places — "
          + "the row in lynq-app-backend and the PDF in lynq-file-storage — and neither service "
          + "can see the other, so this gateway deletes both. The row goes first: it is what the "
          + "candidate sees and the only step that can legitimately fail (a resume that is not "
          + "theirs answers 404). Dropping the PDF afterwards is best effort — once the row is "
          + "gone the file is unreachable from the product, so a storage failure leaves an orphan "
          + "rather than a resume the user was told they deleted and still see.")
  @ApiResponses({
      @ApiResponse(responseCode = "204", description = "Resume deleted."),
      @ApiResponse(responseCode = "401", description = "The Authorization header is missing, or the "
          + "access token's signature is invalid or expired."),
      @ApiResponse(responseCode = "403", description = "The lynq-request-uuid header is missing, or "
          + "the caller is not a candidate."),
      @ApiResponse(responseCode = "502", description = "lynq-app-backend could not be reached, or "
          + "no such resume belongs to the caller.")
  })
  ResponseEntity<Void> deleteResume(
      @Parameter(description = "Id of the resume to delete.") String resumeId,
      @Parameter(hidden = true) String requestUuid,
      @Parameter(hidden = true) String authorization,
      @Parameter(hidden = true) String userId);

}
