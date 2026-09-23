package com.lynq.bff.controller;

import com.lynq.bff.controller.request.PreviewResumeRequest;
import com.lynq.bff.controller.request.TailorApplyRestRequest;
import com.lynq.bff.controller.request.TailorTurnRestRequest;
import com.lynq.bff.controller.request.TranslateResumeRestRequest;
import com.lynq.bff.controller.request.UpdateResumeAliasRestRequest;
import com.lynq.bff.controller.response.GlobalRestResponse;
import com.lynq.bff.controller.response.ResumePreviewRestResponse;
import com.lynq.bff.controller.response.ResumeTailorApplyRestResponse;
import java.util.Map;
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
      summary = "Translate a stored resume into another language",
      description = "Translates one of the candidate's stored resumes and returns the translated "
          + "structured JSON — nothing is rendered or stored here. The candidate continues the "
          + "flow themselves: they pick a template and render a preview through "
          + "POST /resume/preview, and confirming that preview stores the resume through "
          + "lynq-app-backend's POST /user/resume with the previewed fileId. The target language "
          + "must be one lynq-app-backend's supported_languages table offers, and one the "
          + "candidate does not already hold a resume in — translating into the source's own "
          + "language is therefore rejected too.")
  @ApiResponses({
      @ApiResponse(
          responseCode = "200",
          description = "Resume translated; the structured JSON is returned for the preview step.",
          content = @Content(
              examples = @ExampleObject(
                  name = "Translated resume JSON",
                  value = """
                      {
                        "success": true,
                        "data": {
                          "personal_info": { "full_name": "Jane Doe" },
                          "summary": "Ingénieure backend spécialisée en systèmes distribués."
                        }
                      }"""))),
      @ApiResponse(responseCode = "400", description = "The target language is missing or not "
          + "supported, the candidate already holds a resume in it, or no such resume belongs to "
          + "the caller."),
      @ApiResponse(responseCode = "401", description = "The Authorization header is missing, or the "
          + "access token's signature is invalid or expired."),
      @ApiResponse(responseCode = "403", description = "The lynq-request-uuid header is missing, or "
          + "the caller is not a CANDIDATE."),
      @ApiResponse(responseCode = "502", description = "A service the translation depends on "
          + "failed; nothing was changed.")
  })
  ResponseEntity<GlobalRestResponse<Object>> translateResume(
      @Parameter(
          name = "resumeId",
          in = ParameterIn.PATH,
          required = true,
          description = "Id of the stored resume to translate.",
          example = "018f9c3a-2b1d-7c4e-9a6f-1e2d3c4b5a60")
      String resumeId,
      TranslateResumeRestRequest request,
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
      summary = "Assign or replace the alias of a stored resume",
      description = "Sets the alias the candidate uses to tell one of their resumes apart from "
          + "the others — assigning for the first time and renaming are the same operation, the "
          + "new alias simply overrides the previous one. The gateway validates the alias, checks "
          + "the token carries the CANDIDATE role, and relays to lynq-app-backend's "
          + "PUT /user/resume/{resumeId}/alias, which enforces that the resume belongs to the "
          + "caller.")
  @ApiResponses({
      @ApiResponse(
          responseCode = "200",
          description = "Alias saved; the updated resume is returned.",
          content = @Content(
              examples = @ExampleObject(
                  name = "Resume with alias",
                  value = """
                      {
                        "success": true,
                        "data": {
                          "id": "018f9c3a-2b1d-7c4e-9a6f-1e2d3c4b5a60",
                          "name": "Jane Doe",
                          "alias": "Backend roles",
                          "language": "EN",
                          "createdOn": "2026-09-02",
                          "resume": { "personal_info": { "full_name": "Jane Doe" } },
                          "pdfUrl": "https://lynq-bucket.s3.amazonaws.com/lynq/0195f2c1-3b1a-7c2d-9f31-3f6a5f2c9d41/resume.pdf?X-Amz-Signature=..."
                        }
                      }"""))),
      @ApiResponse(responseCode = "400", description = "The alias is missing, blank, or longer "
          + "than 100 characters."),
      @ApiResponse(responseCode = "401", description = "The Authorization header is missing, or the "
          + "access token's signature is invalid or expired."),
      @ApiResponse(responseCode = "403", description = "The lynq-request-uuid header is missing, or "
          + "the caller is not a CANDIDATE."),
      @ApiResponse(responseCode = "502", description = "lynq-app-backend could not be reached, or "
          + "no such resume belongs to the caller.")
  })
  ResponseEntity<GlobalRestResponse<Object>> updateResumeAlias(
      @Parameter(
          name = "resumeId",
          in = ParameterIn.PATH,
          required = true,
          description = "Id of the stored resume the alias is assigned to.",
          example = "018f9c3a-2b1d-7c4e-9a6f-1e2d3c4b5a60")
      String resumeId,
      UpdateResumeAliasRestRequest request,
      @Parameter(hidden = true) String requestUuid,
      @Parameter(hidden = true) String authorization,
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

  @Operation(
      summary = "Start a CV Tailor conversation for a job posting",
      description = "Opens the conversation in which the agent adapts one of the candidate's "
          + "stored resumes to a job posting. The gateway reads the posting from "
          + "lynq-app-backend itself — the browser never supplies it, or anyone could push an "
          + "arbitrary description into the agent's prompt — checks the resume belongs to the "
          + "caller, and sends lynq-agent the posting, the frozen resume, the language the agent "
          + "replies in (the caller's UI locale) and the language the resume is written in, which "
          + "is the resume's own and never guessed. The agent answers with the conversation id "
          + "and its greeting; adapting the resume takes a turn.")
  @ApiResponses({
      @ApiResponse(
          responseCode = "201",
          description = "Conversation started.",
          content = @Content(
              examples = @ExampleObject(
                  name = "Conversation",
                  value = """
                      {
                        "success": true,
                        "data": {
                          "conversationId": "0195f2c1-3b1a-7c2d-9f31-3f6a5f2c9d42",
                          "greeting": "Miré el aviso de Senior Backend Engineer en Acme. ¿Armo una versión de tu CV apuntada a este puesto?",
                          "status": "AWAITING_CONFIRMATION"
                        }
                      }"""))),
      @ApiResponse(responseCode = "400", description = "No such resume belongs to the caller."),
      @ApiResponse(responseCode = "401", description = "The Authorization header is missing, or the "
          + "access token's signature is invalid or expired."),
      @ApiResponse(responseCode = "403", description = "The lynq-request-uuid header is missing, or "
          + "the caller is not a CANDIDATE."),
      @ApiResponse(responseCode = "404", description = "No such job posting."),
      @ApiResponse(responseCode = "502", description = "A service the flow depends on failed.")
  })
  ResponseEntity<GlobalRestResponse<Object>> startResumeTailoring(
      @Parameter(
          name = "resumeId",
          in = ParameterIn.PATH,
          required = true,
          description = "Id of the stored resume the agent adapts.",
          example = "018f9c3a-2b1d-7c4e-9a6f-1e2d3c4b5a60")
      String resumeId,
      @Parameter(
          name = "jobId",
          in = ParameterIn.PATH,
          required = true,
          description = "Id of the job posting the resume is adapted to.",
          example = "018f9c3a-2b1d-7c4e-9a6f-1e2d3c4b5a61")
      String jobId,
      @Parameter(
          name = "language",
          in = ParameterIn.QUERY,
          description = "The caller's UI language: the one the agent replies in. The resume is "
              + "edited in its own language regardless.",
          example = "ES")
      String language,
      @Parameter(hidden = true) String requestUuid,
      @Parameter(hidden = true) String authorization,
      @Parameter(hidden = true) String userId);

  @Operation(
      summary = "Take a turn in a CV Tailor conversation",
      description = "Sends the candidate's message to the agent and returns the resume as it "
          + "stands after it, with what changed, what the agent refused to invent and how many "
          + "turns are left. The turnKey is the caller's idempotency key: repeating a turn with "
          + "the same key returns the same answer instead of spending another one. A turn runs an "
          + "LLM loop and can take minutes.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Turn completed."),
      @ApiResponse(responseCode = "400", description = "The message or the turn key is missing."),
      @ApiResponse(responseCode = "401", description = "The Authorization header is missing, or the "
          + "access token's signature is invalid or expired."),
      @ApiResponse(responseCode = "403", description = "The lynq-request-uuid header is missing, "
          + "the caller is not a CANDIDATE, or the conversation belongs to another user."),
      @ApiResponse(responseCode = "404", description = "No such conversation."),
      @ApiResponse(
          responseCode = "409",
          description = "A turn is already running (TURN_IN_PROGRESS) or the conversation has run "
              + "out of turns (CONVERSATION_EXHAUSTED). The envelope carries the code.",
          content = @Content(
              examples = @ExampleObject(
                  name = "Turn in progress",
                  value = """
                      {
                        "success": false,
                        "data": null,
                        "reason": "A turn is already running on this conversation",
                        "code": "TURN_IN_PROGRESS"
                      }"""))),
      @ApiResponse(responseCode = "502", description = "The agent could not finish the turn.")
  })
  ResponseEntity<GlobalRestResponse<Object>> takeResumeTailoringTurn(
      @Parameter(
          name = "conversationId",
          in = ParameterIn.PATH,
          required = true,
          description = "Id of the conversation, as returned when it was started.",
          example = "0195f2c1-3b1a-7c2d-9f31-3f6a5f2c9d42")
      String conversationId,
      TailorTurnRestRequest request,
      @Parameter(hidden = true) String requestUuid,
      @Parameter(hidden = true) String authorization,
      @Parameter(hidden = true) String userId);

  @Operation(
      summary = "Read a CV Tailor conversation",
      description = "Returns the conversation as it stands — its status, the thread, the resume "
          + "currently in force and the versions behind it — so the modal can be reopened where "
          + "the candidate left it. The agent serves it only to the user it belongs to.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Conversation returned."),
      @ApiResponse(responseCode = "401", description = "The Authorization header is missing, or the "
          + "access token's signature is invalid or expired."),
      @ApiResponse(responseCode = "403", description = "The lynq-request-uuid header is missing, "
          + "the caller is not a CANDIDATE, or the conversation belongs to another user."),
      @ApiResponse(responseCode = "404", description = "No such conversation."),
      @ApiResponse(responseCode = "502", description = "lynq-agent could not be reached.")
  })
  ResponseEntity<GlobalRestResponse<Map<String, Object>>> getResumeTailoringConversation(
      @Parameter(
          name = "conversationId",
          in = ParameterIn.PATH,
          required = true,
          description = "Id of the conversation to read.",
          example = "0195f2c1-3b1a-7c2d-9f31-3f6a5f2c9d42")
      String conversationId,
      @Parameter(hidden = true) String requestUuid,
      @Parameter(hidden = true) String authorization,
      @Parameter(hidden = true) String userId);

  @Operation(
      summary = "Apply with the tailored resume and close the conversation",
      description = "The orchestrated close of the flow. The candidate has already stored the "
          + "tailored resume through POST /user/resume; this checks it belongs to the caller, "
          + "reads from the conversation which posting it was started for, applies to that "
          + "posting with it against lynq-app-backend and tells lynq-agent the conversation is "
          + "applied. Having already applied to the posting is not an error: the conversation is "
          + "closed all the same and the response says so, which is the meaning the browser "
          + "already gives that case on external postings. The application is what comes back, so "
          + "the browser never calls the apply endpoint by itself and leaves the conversation "
          + "open behind it.")
  @ApiResponses({
      @ApiResponse(
          responseCode = "201",
          description = "Applied and conversation closed.",
          content = @Content(
              schema = @Schema(implementation = ResumeTailorApplyRestResponse.class),
              examples = @ExampleObject(
                  name = "Application",
                  value = """
                      {
                        "success": true,
                        "data": {
                          "application": {
                            "applicationId": "018fa1b2-2b1d-7c4e-9a6f-1e2d3c4b5a62",
                            "jobId": "018f9c3a-2b1d-7c4e-9a6f-1e2d3c4b5a61",
                            "userId": "018f9c3a-2b1d-7c4e-9a6f-1e2d3c4b5a11",
                            "appliedOn": "2026-09-23"
                          },
                          "alreadyApplied": false,
                          "conversationStatus": "APPLIED"
                        }
                      }"""))),
      @ApiResponse(responseCode = "400", description = "The resume id is missing, or no such "
          + "resume belongs to the caller."),
      @ApiResponse(responseCode = "401", description = "The Authorization header is missing, or the "
          + "access token's signature is invalid or expired."),
      @ApiResponse(responseCode = "403", description = "The lynq-request-uuid header is missing, "
          + "the caller is not a CANDIDATE, or the conversation belongs to another user."),
      @ApiResponse(responseCode = "404", description = "No such conversation."),
      @ApiResponse(responseCode = "409", description = "The conversation was already closed with a "
          + "different resume (ALREADY_APPLIED). The envelope carries the code."),
      @ApiResponse(responseCode = "502", description = "A service the close depends on failed.")
  })
  ResponseEntity<GlobalRestResponse<ResumeTailorApplyRestResponse>> applyWithTailoredResume(
      @Parameter(
          name = "conversationId",
          in = ParameterIn.PATH,
          required = true,
          description = "Id of the conversation being closed.",
          example = "0195f2c1-3b1a-7c2d-9f31-3f6a5f2c9d42")
      String conversationId,
      TailorApplyRestRequest request,
      @Parameter(hidden = true) String requestUuid,
      @Parameter(hidden = true) String authorization,
      @Parameter(hidden = true) String userId);

}
