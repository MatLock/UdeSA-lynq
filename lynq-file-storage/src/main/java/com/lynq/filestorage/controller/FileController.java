package com.lynq.filestorage.controller;

import com.lynq.filestorage.controller.request.CreateFileDownloadBatchRequest;
import com.lynq.filestorage.controller.request.CreateFileUploadRequest;
import com.lynq.filestorage.controller.response.CreateFileDownloadRestResponse;
import com.lynq.filestorage.controller.response.CreateFileUploadRestResponse;
import com.lynq.filestorage.controller.response.FileRestResponse;
import com.lynq.filestorage.controller.response.GlobalRestResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.ResponseEntity;

@Tag(name = "File", description = "Operations for storing and retrieving Lynq platform files")
public interface FileController {

  @Operation(summary = "Register a file and get a pre-signed upload URL")
  @ApiResponses({
      @ApiResponse(responseCode = "201", description = "File registered as PENDING"),
      @ApiResponse(responseCode = "400", description = "Invalid request body"),
      @ApiResponse(responseCode = "403", description = "Missing lynq-request-uuid header")
  })
  ResponseEntity<GlobalRestResponse<CreateFileUploadRestResponse>> createUpload(
      @Valid CreateFileUploadRequest request, String userId);

  @Operation(summary = "Confirm a finished upload and mark the file as AVAILABLE")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "File confirmed"),
      @ApiResponse(responseCode = "400", description = "The object is not in the bucket yet"),
      @ApiResponse(responseCode = "403", description = "The file belongs to another user"),
      @ApiResponse(responseCode = "404", description = "Unknown file")
  })
  ResponseEntity<GlobalRestResponse<FileRestResponse>> confirmUpload(String fileId, String userId);

  @Operation(summary = "Get a pre-signed download URL for a file")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Download URL issued"),
      @ApiResponse(responseCode = "404", description = "Unknown file")
  })
  ResponseEntity<GlobalRestResponse<CreateFileDownloadRestResponse>> createDownloadUrl(String fileId);

  @Operation(summary = "Get pre-signed download URLs for up to 100 files, keyed by file id. "
      + "Unknown ids are omitted from the response instead of failing the batch")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "Download URLs issued"),
      @ApiResponse(responseCode = "400", description = "Empty batch or more than 100 ids")
  })
  ResponseEntity<GlobalRestResponse<Map<String, String>>> createDownloadUrls(
      @Valid CreateFileDownloadBatchRequest request);

  @Operation(summary = "Delete a file from the bucket and forget its metadata. Idempotent: "
      + "deleting an unknown id succeeds without doing anything")
  @ApiResponses({
      @ApiResponse(responseCode = "204", description = "File deleted"),
      @ApiResponse(responseCode = "403",
          description = "Missing lynq-request-uuid header, or the file belongs to another user")
  })
  ResponseEntity<Void> deleteFile(String fileId, String userId);

}
