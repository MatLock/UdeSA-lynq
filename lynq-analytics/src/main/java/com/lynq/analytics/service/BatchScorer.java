package com.lynq.analytics.service;

import com.lynq.analytics.client.LynqBackendClient;
import com.lynq.analytics.client.request.ScoreBatchRequest;
import com.lynq.analytics.client.request.ScoreBatchRequest.ScorePair;
import com.lynq.analytics.client.request.ScoreBatchRequest.ScoredProfile;
import com.lynq.analytics.client.response.ScoreBatchResponse;
import com.lynq.analytics.config.BenchmarkProperties;
import com.lynq.analytics.controller.response.GlobalRestResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.extern.log4j.Log4j2;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Log4j2
public class BatchScorer {

  private static final String MDC_REQUEST_ID = "requestId";

  private final LynqBackendClient lynqBackendClient;
  private final String internalToken;
  private final int pairsPerRequest;

  public BatchScorer(LynqBackendClient lynqBackendClient,
      @Value("${lynq.internal.token:}") String internalToken, BenchmarkProperties properties) {
    this.lynqBackendClient = lynqBackendClient;
    this.internalToken = internalToken;
    this.pairsPerRequest = properties.pairsPerRequest();
  }

  public Map<ScorePair, Integer> score(List<ProfilePair> pairs) {
    Map<ScorePair, Integer> scores = new HashMap<>();
    String requestUuid = requestUuid();
    for (int from = 0; from < pairs.size(); from += pairsPerRequest) {
      List<ProfilePair> chunk = pairs.subList(from, Math.min(from + pairsPerRequest, pairs.size()));
      ScoreBatchResponse response = dataOf(
          lynqBackendClient.scoreBatch(internalToken, requestUuid, requestOf(chunk)));
      response.scores().forEach(scored -> scores.put(
          new ScorePair(scored.jobId(), scored.candidateId()), scored.score()));
    }
    log.info("message= Scored {} pairs in {} requests", pairs.size(),
        (pairs.size() + pairsPerRequest - 1) / pairsPerRequest);
    return scores;
  }

  private static ScoreBatchRequest requestOf(List<ProfilePair> chunk) {
    Map<String, ScoredProfile> jobPosts = new LinkedHashMap<>();
    Map<String, ScoredProfile> candidates = new LinkedHashMap<>();
    List<ScorePair> pairs = new ArrayList<>(chunk.size());
    for (ProfilePair pair : chunk) {
      jobPosts.putIfAbsent(pair.jobPost().id(), pair.jobPost());
      candidates.putIfAbsent(pair.candidate().id(), pair.candidate());
      pairs.add(new ScorePair(pair.jobPost().id(), pair.candidate().id()));
    }
    return new ScoreBatchRequest(List.copyOf(jobPosts.values()),
        List.copyOf(candidates.values()), pairs);
  }

  private static ScoreBatchResponse dataOf(GlobalRestResponse<ScoreBatchResponse> response) {
    if (response == null || response.getData() == null || response.getData().scores() == null) {
      throw new IllegalStateException("lynq-app-backend answered the score batch without scores");
    }
    return response.getData();
  }

  private static String requestUuid() {
    String requestId = MDC.get(MDC_REQUEST_ID);
    return requestId != null ? requestId : UUID.randomUUID().toString();
  }

  public record ProfilePair(ScoredProfile jobPost, ScoredProfile candidate) {
  }
}
