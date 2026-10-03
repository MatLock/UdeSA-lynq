package com.lynq.backend.service;

import com.lynq.backend.controller.request.ScoreBatchPairRequest;
import com.lynq.backend.controller.request.ScoreBatchProfileRequest;
import com.lynq.backend.controller.request.ScoreBatchRequest;
import com.lynq.backend.controller.response.PairScoreRestResponse;
import com.lynq.backend.controller.response.ScoreBatchRestResponse;
import com.lynq.backend.exceptions.BadRequestException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class ScoreBatchService {

  public ScoreBatchRestResponse score(ScoreBatchRequest request) {
    Map<String, ScoreBatchProfileRequest> jobPosts = byId(request.getJobPosts(), "job post");
    Map<String, ScoreBatchProfileRequest> candidates = byId(request.getCandidates(), "candidate");

    List<PairScoreRestResponse> scores = request.getPairs().stream()
        .map(pair -> score(pair, profileOf(jobPosts, pair.getJobId(), "job post"),
            profileOf(candidates, pair.getCandidateId(), "candidate")))
        .toList();

    return ScoreBatchRestResponse.builder().scores(scores).build();
  }

  private static PairScoreRestResponse score(ScoreBatchPairRequest pair,
      ScoreBatchProfileRequest jobPost, ScoreBatchProfileRequest candidate) {
    int score = LyNQScoreCalculator.score(
        listOrEmpty(jobPost.getSkills()), listOrEmpty(jobPost.getSimilarityTags()),
        listOrEmpty(candidate.getSkills()), listOrEmpty(candidate.getSimilarityTags()));
    return PairScoreRestResponse.builder()
        .jobId(pair.getJobId())
        .candidateId(pair.getCandidateId())
        .score(score)
        .build();
  }

  private static Map<String, ScoreBatchProfileRequest> byId(
      List<ScoreBatchProfileRequest> profiles, String kind) {
    return profiles.stream().collect(Collectors.toMap(ScoreBatchProfileRequest::getId,
        Function.identity(), (first, second) -> {
          throw new BadRequestException("Duplicate " + kind + " id '" + first.getId() + "'");
        }));
  }

  private static ScoreBatchProfileRequest profileOf(Map<String, ScoreBatchProfileRequest> profiles,
      String id, String kind) {
    ScoreBatchProfileRequest profile = profiles.get(id);
    if (profile == null) {
      throw new BadRequestException("Pair references unknown " + kind + " '" + id + "'");
    }
    return profile;
  }

  private static List<String> listOrEmpty(List<String> values) {
    return values == null ? List.of() : values;
  }
}
