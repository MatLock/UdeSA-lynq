package com.lynq.backend.service;

import com.lynq.backend.controller.request.ScoreBatchPairRequest;
import com.lynq.backend.controller.request.ScoreBatchProfileRequest;
import com.lynq.backend.controller.request.ScoreBatchRequest;
import com.lynq.backend.controller.response.PairScoreRestResponse;
import com.lynq.backend.controller.response.ScoreBatchRestResponse;
import com.lynq.backend.exceptions.BadRequestException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ScoreBatchServiceTest {

  private static final ScoreBatchProfileRequest BACKEND_JOB = profile("job-backend",
      List.of("Java", "Spring", "MySQL", "Kafka"), List.of("Backend Development", "Messaging"));
  private static final ScoreBatchProfileRequest DATA_JOB = profile("job-data",
      List.of("Python", "Airflow"), List.of("Data Engineering"));
  private static final ScoreBatchProfileRequest EMPTY_JOB = profile("job-empty", null, null);

  private static final ScoreBatchProfileRequest JAVA_CANDIDATE = profile("candidate-java",
      List.of(" java ", "spring"), List.of("Backend Development"));
  private static final ScoreBatchProfileRequest NOBODY = profile("candidate-nobody", null, null);

  private final ScoreBatchService scoreBatchService = new ScoreBatchService();

  @Test
  void scoresEveryPairAgainstTheProfilesTheRequestDescribes() {
    ScoreBatchRestResponse response = scoreBatchService.score(request(
        List.of(BACKEND_JOB, DATA_JOB), List.of(JAVA_CANDIDATE),
        pair("job-backend", "candidate-java"), pair("job-data", "candidate-java")));

    assertThat(response.getScores().stream().map(PairScoreRestResponse::getScore).toList(),
        contains(50, 0));
    assertThat(response.getScores().get(0).getJobId(), is("job-backend"));
    assertThat(response.getScores().get(0).getCandidateId(), is("candidate-java"));
  }

  @Test
  void keepsTheBetterOfTheSkillsAndTheTagsCoverage() {
    ScoreBatchProfileRequest messagingCandidate = profile("candidate-messaging",
        List.of("RabbitMQ"), List.of("Backend Development", "Messaging"));

    ScoreBatchRestResponse response = scoreBatchService.score(request(
        List.of(BACKEND_JOB), List.of(messagingCandidate),
        pair("job-backend", "candidate-messaging")));

    assertThat(response.getScores().getFirst().getScore(), is(100));
  }

  @Test
  void scoresZeroWhenEitherSideHasNothingRecorded() {
    ScoreBatchRestResponse response = scoreBatchService.score(request(
        List.of(BACKEND_JOB, EMPTY_JOB), List.of(JAVA_CANDIDATE, NOBODY),
        pair("job-backend", "candidate-nobody"), pair("job-empty", "candidate-java")));

    assertThat(response.getScores().stream().map(PairScoreRestResponse::getScore).toList(),
        contains(0, 0));
  }

  @Test
  void answersThePairsInTheOrderTheyWereAskedForRepeatsIncluded() {
    ScoreBatchRestResponse response = scoreBatchService.score(request(
        List.of(BACKEND_JOB, DATA_JOB), List.of(JAVA_CANDIDATE),
        pair("job-data", "candidate-java"), pair("job-backend", "candidate-java"),
        pair("job-data", "candidate-java")));

    assertThat(response.getScores().stream().map(PairScoreRestResponse::getJobId).toList(),
        contains("job-data", "job-backend", "job-data"));
  }

  @Test
  void rejectsAPairNamingAJobPostTheRequestDoesNotDescribe() {
    ScoreBatchRequest request = request(List.of(BACKEND_JOB), List.of(JAVA_CANDIDATE),
        pair("job-missing", "candidate-java"));

    BadRequestException thrown =
        assertThrows(BadRequestException.class, () -> scoreBatchService.score(request));

    assertThat(thrown.getMessage(), is("Pair references unknown job post 'job-missing'"));
  }

  @Test
  void rejectsAPairNamingACandidateTheRequestDoesNotDescribe() {
    ScoreBatchRequest request = request(List.of(BACKEND_JOB), List.of(JAVA_CANDIDATE),
        pair("job-backend", "candidate-missing"));

    BadRequestException thrown =
        assertThrows(BadRequestException.class, () -> scoreBatchService.score(request));

    assertThat(thrown.getMessage(), is("Pair references unknown candidate 'candidate-missing'"));
  }

  @Test
  void rejectsAnIdDescribedTwice() {
    ScoreBatchRequest request = request(List.of(BACKEND_JOB),
        List.of(JAVA_CANDIDATE, profile("candidate-java", List.of("Go"), null)),
        pair("job-backend", "candidate-java"));

    BadRequestException thrown =
        assertThrows(BadRequestException.class, () -> scoreBatchService.score(request));

    assertThat(thrown.getMessage(), is("Duplicate candidate id 'candidate-java'"));
  }

  private static ScoreBatchRequest request(List<ScoreBatchProfileRequest> jobPosts,
      List<ScoreBatchProfileRequest> candidates, ScoreBatchPairRequest... pairs) {
    return ScoreBatchRequest.builder()
        .jobPosts(jobPosts)
        .candidates(candidates)
        .pairs(List.of(pairs))
        .build();
  }

  private static ScoreBatchProfileRequest profile(String id, List<String> skills,
      List<String> similarityTags) {
    return ScoreBatchProfileRequest.builder()
        .id(id)
        .skills(skills)
        .similarityTags(similarityTags)
        .build();
  }

  private static ScoreBatchPairRequest pair(String jobId, String candidateId) {
    return ScoreBatchPairRequest.builder().jobId(jobId).candidateId(candidateId).build();
  }
}
