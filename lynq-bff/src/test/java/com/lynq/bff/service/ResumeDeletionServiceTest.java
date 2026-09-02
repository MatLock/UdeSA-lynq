package com.lynq.bff.service;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lynq.bff.client.LynqBackendClient;
import com.lynq.bff.client.LynqFileStorageClient;
import com.lynq.bff.client.response.DeletedResumeResponse;
import com.lynq.bff.client.response.UserResponse;
import com.lynq.bff.controller.response.GlobalRestResponse;
import com.lynq.bff.exceptions.BadGatewayException;
import com.lynq.bff.exceptions.ForbiddenException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ResumeDeletionServiceTest {

  private static final String USER_ID = "11111111-1111-1111-1111-111111111111";
  private static final String REQUEST_UUID = "018f9c3a-2b1d-7c4e-9a6f-1e2d3c4b5a99";
  private static final String AUTHORIZATION = "Bearer access-token";
  private static final Caller CALLER = new Caller(USER_ID, REQUEST_UUID, AUTHORIZATION);

  private static final String RESUME_ID = "018f9c3a-2b1d-7c4e-9a6f-1e2d3c4b5a60";
  private static final String FILE_ID = "0195f2c1-3b1a-7c2d-9f31-3f6a5f2c9d41";

  @Mock
  private CandidateReader candidateReader;

  @Mock
  private LynqBackendClient lynqBackendClient;

  @Mock
  private LynqFileStorageClient lynqFileStorageClient;

  private ResumeDeletionService resumeDeletionService;

  @BeforeEach
  void setUp() {
    resumeDeletionService = new ResumeDeletionService(
        candidateReader, lynqBackendClient, lynqFileStorageClient);
  }

  @Test
  void deletesTheResumeRowAndThenItsStoredPdf() {
    stubDeleted(FILE_ID);

    resumeDeletionService.delete(RESUME_ID, CALLER);

    // Order matters: the row is what the candidate sees, so it goes first. Only
    // once it is gone is the PDF unreachable from the product.
    InOrder order = inOrder(lynqBackendClient, lynqFileStorageClient);
    order.verify(lynqBackendClient).deleteResume(RESUME_ID, REQUEST_UUID, AUTHORIZATION);
    order.verify(lynqFileStorageClient).deleteFile(FILE_ID, REQUEST_UUID, USER_ID);
  }

  @Test
  void rejectsCallersThatAreNotCandidates() {
    when(candidateReader.read(any())).thenThrow(new ForbiddenException("nope"));

    assertThrows(ForbiddenException.class, () -> resumeDeletionService.delete(RESUME_ID, CALLER));
    verify(lynqBackendClient, never()).deleteResume(any(), any(), any());
    verify(lynqFileStorageClient, never()).deleteFile(any(), any(), any());
  }

  @Test
  void failsWithBadGatewayWhenTheBackendCannotDeleteTheResume() {
    when(candidateReader.read(any())).thenReturn(candidate());
    when(lynqBackendClient.deleteResume(RESUME_ID, REQUEST_UUID, AUTHORIZATION))
        .thenThrow(new IllegalStateException("boom"));

    BadGatewayException exception = assertThrows(BadGatewayException.class,
        () -> resumeDeletionService.delete(RESUME_ID, CALLER));

    assertThat(exception.getMessage(), is("The resume could not be deleted"));
    // The row survived, so the file must not be touched.
    verify(lynqFileStorageClient, never()).deleteFile(any(), any(), any());
  }

  @Test
  void survivesAStorageFailureBecauseTheResumeIsAlreadyGone() {
    // Reporting an error here would contradict what the candidate just saw
    // happen; the orphaned file is logged instead.
    stubDeleted(FILE_ID);
    doThrow(new IllegalStateException("storage down"))
        .when(lynqFileStorageClient).deleteFile(FILE_ID, REQUEST_UUID, USER_ID);

    resumeDeletionService.delete(RESUME_ID, CALLER);

    verify(lynqFileStorageClient).deleteFile(FILE_ID, REQUEST_UUID, USER_ID);
  }

  @Test
  void skipsStorageWhenTheResumeHadNoPdf() {
    stubDeleted(null);

    resumeDeletionService.delete(RESUME_ID, CALLER);

    verify(lynqFileStorageClient, never()).deleteFile(any(), any(), any());
  }

  private void stubDeleted(String fileId) {
    when(candidateReader.read(any())).thenReturn(candidate());
    DeletedResumeResponse deleted = DeletedResumeResponse.builder()
        .id(RESUME_ID)
        .fileId(fileId)
        .build();
    when(lynqBackendClient.deleteResume(RESUME_ID, REQUEST_UUID, AUTHORIZATION))
        .thenReturn(new GlobalRestResponse<>(true, deleted));
  }

  private UserResponse candidate() {
    return UserResponse.builder().id(USER_ID).userType("CANDIDATE").build();
  }
}
