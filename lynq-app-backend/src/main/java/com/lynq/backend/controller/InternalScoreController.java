package com.lynq.backend.controller;

import com.lynq.backend.controller.request.ScoreBatchRequest;
import com.lynq.backend.controller.response.GlobalRestResponse;
import com.lynq.backend.controller.response.ScoreBatchRestResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;

@Tag(name = "Internal", description = "Service-to-service operations, not reachable from the DMZ")
public interface InternalScoreController {

  @Operation(
      summary = "Score a batch of candidate and job post pairs",
      description = "Computes the LyNQ score of every pair with the same calculator the feed and "
          + "the applications use, over the skills and similarity tags sent in the request. "
          + "Nothing is read from or written to the database: the caller describes each job post "
          + "and each candidate once and lists the pairs it needs, up to 5000 of each. A pair "
          + "naming an id the request does not describe, or an id described twice, fails with "
          + "400. Authenticated with the shared internal token header.")
  ResponseEntity<GlobalRestResponse<ScoreBatchRestResponse>> scoreBatch(
      @Valid ScoreBatchRequest request);
}
