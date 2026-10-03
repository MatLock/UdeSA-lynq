package com.lynq.analytics.client.response;

import java.util.List;

public record ScoreBatchResponse(List<PairScore> scores) {

  public record PairScore(String jobId, String candidateId, int score) {
  }
}
