package com.lynq.bff.controller;

import com.lynq.bff.client.request.CreateFileDownloadBatchRequest;
import com.lynq.bff.client.request.CreateFileUploadRequest;
import com.lynq.bff.client.response.CreateFileDownloadResponse;
import com.lynq.bff.client.response.CreateFileUploadResponse;
import com.lynq.bff.client.response.FileResponse;
import com.lynq.bff.controller.response.GlobalRestResponse;
import com.lynq.bff.security.LynqUserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import java.util.Map;
import org.springframework.http.ResponseEntity;

@ApiResponses({
    @ApiResponse(responseCode = "401", description = "The Authorization header is missing, or the "
        + "access token's signature is invalid or expired."),
    @ApiResponse(responseCode = "403", description = "The lynq-request-uuid header is missing, or "
        + "the file belongs to another user."),
    @ApiResponse(responseCode = "502", description = "lynq-file-storage could not be reached.")
})
public interface FileController {

  @Operation(
      summary = "Register a file and get the pre-signed url to upload it",
      description = "The bucket credentials never leave lynq-file-storage, and the pre-signed url "
          + "it hands back points straight at S3, not back through here.")
  ResponseEntity<GlobalRestResponse<CreateFileUploadResponse>> createUpload(
      CreateFileUploadRequest request,
      @Parameter(hidden = true) String requestUuid,
      @Parameter(hidden = true) LynqUserPrincipal principal);

  @Operation(summary = "Confirm that a registered file finished uploading")
  ResponseEntity<GlobalRestResponse<FileResponse>> confirmUpload(
      String fileId,
      @Parameter(hidden = true) String requestUuid,
      @Parameter(hidden = true) LynqUserPrincipal principal);

  @Operation(summary = "Read a file the authenticated user registered")
  @ApiResponses(@ApiResponse(responseCode = "404", description = "No file holds that id."))
  ResponseEntity<GlobalRestResponse<FileResponse>> findOwnedFile(
      String fileId,
      @Parameter(hidden = true) String requestUuid,
      @Parameter(hidden = true) LynqUserPrincipal principal);

  @Operation(summary = "Get the pre-signed url to download a file")
  @ApiResponses(@ApiResponse(responseCode = "404", description = "No file holds that id."))
  ResponseEntity<GlobalRestResponse<CreateFileDownloadResponse>> createDownloadUrl(
      String fileId,
      @Parameter(hidden = true) String requestUuid,
      @Parameter(hidden = true) LynqUserPrincipal principal);

  @Operation(summary = "Get the pre-signed urls of several files at once")
  ResponseEntity<GlobalRestResponse<Map<String, String>>> createDownloadUrls(
      CreateFileDownloadBatchRequest request,
      @Parameter(hidden = true) String requestUuid,
      @Parameter(hidden = true) LynqUserPrincipal principal);

  @Operation(summary = "Delete a file the authenticated user registered")
  ResponseEntity<Void> deleteFile(
      String fileId,
      @Parameter(hidden = true) String requestUuid,
      @Parameter(hidden = true) LynqUserPrincipal principal);
}
