package com.lynq.analytics.client.request;

import java.util.List;

public record ScoreBatchRequest(List<ScoredProfile> jobPosts, List<ScoredProfile> candidates,
    List<ScorePair> pairs) {

  public record ScoredProfile(String id, List<String> skills, List<String> similarityTags) {
  }

  public record ScorePair(String jobId, String candidateId) {
  }
}
