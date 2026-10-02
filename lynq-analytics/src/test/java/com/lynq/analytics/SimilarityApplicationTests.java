package com.lynq.analytics;

import com.lynq.analytics.enums.JobStatus;
import com.lynq.analytics.model.CandidateEntity;
import com.lynq.analytics.model.JobPostEntity;
import com.lynq.analytics.model.TagFrequencyEntity;
import com.lynq.analytics.repository.ApplicationRepository;
import com.lynq.analytics.repository.CandidateRepository;
import com.lynq.analytics.repository.JobPostRepository;
import com.lynq.analytics.repository.TagFrequencyRepository;
import com.lynq.analytics.service.SimilarityService;
import com.lynq.analytics.service.TagFrequencyService;
import com.lynq.analytics.similarity.SimilarMatches;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

class SimilarityApplicationTests extends AbstractE2ETest {

  private static final Instant OCCURRED_ON = Instant.parse("2026-09-20T10:00:00Z");
  private static final Set<String> NARROW = Set.of("N1", "N2", "N3", "N4", "N5");

  private static final Map<String, Set<String>> JOB_POSTS = jobPosts();

  @Autowired
  private ApplicationRepository applicationRepository;

  @Autowired
  private JobPostRepository jobPostRepository;

  @Autowired
  private CandidateRepository candidateRepository;

  @Autowired
  private TagFrequencyRepository tagFrequencyRepository;

  @Autowired
  private TagFrequencyService tagFrequencyService;

  @Autowired
  private SimilarityService similarityService;

  @BeforeEach
  void setUp() {
    applicationRepository.deleteAll();
    jobPostRepository.deleteAll();
    candidateRepository.deleteAll();
    tagFrequencyRepository.deleteAll();
    JOB_POSTS.forEach((id, tags) -> jobPostRepository.save(jobPost(id, tags)));
    tagFrequencyService.recompute();
  }

  @Test
  void recomputesTheWeightOfEveryTagOverTheJobPosts() {
    assertThat(tagFrequencyRepository.count(), is(11L));
    assertFrequency("teamwork", 13, 0.0);
    assertFrequency("backend", 8, Math.log(14.0 / 9));
    assertFrequency("kubernetes", 1, Math.log(7));
  }

  @Test
  void removesATagNoJobPostHasAnymore() {
    jobPostRepository.deleteById("RARE");

    tagFrequencyService.recompute();

    assertThat(tagFrequencyRepository.existsById("kubernetes"), is(false));
    assertFrequency("teamwork", 12, 0.0);
  }

  @Test
  void aNarrowJobPostWithTheSameTagsIsSimilarAndABroadOneContainingThemIsNot() {
    SimilarMatches<JobPostEntity> matches = similarityService.findSimilarJobPosts("REF",
        job -> true);

    assertThat(matches.fallback(), is(false));
    assertThat(matches.threshold(), closeTo(2.0 / 3, 1e-9));
    assertThat(ids(matches), containsInAnyOrder("N1", "N2", "N3", "N4", "N5", "HALF"));
    assertThat(ids(matches).getLast(), is("HALF"));
  }

  @Test
  void fallsBackToOneMedianTagForJobPostsWhenFewerThanFivePass() {
    SimilarMatches<JobPostEntity> matches = similarityService.findSimilarJobPosts("REF",
        job -> !NARROW.contains(job.getId()));

    assertThat(matches.fallback(), is(true));
    assertThat(matches.threshold(), closeTo(1.0 / 3, 1e-9));
    assertThat(ids(matches), contains("HALF", "CLOUDONLY"));
  }

  @Test
  void aTagInEveryJobPostAloneAdmitsNoCandidateAndARareOneAloneDoes() {
    candidateRepository.save(candidate("C_TEAM", Set.of("teamwork")));
    candidateRepository.save(candidate("C_KUBE", Set.of("kubernetes")));

    SimilarMatches<CandidateEntity> matches = similarityService.findSimilarCandidates("RARE",
        candidate -> true);

    assertThat(candidateIds(matches), contains("C_KUBE"));
    assertThat(matches.matches().getFirst().score(), closeTo(Math.log(7), 1e-9));
  }

  @Test
  void fallsBackToOneMedianTagForCandidatesWhenFewerThanFivePass() {
    candidateRepository.save(candidate("C_BS", Set.of("backend", "sql")));
    candidateRepository.save(candidate("C_CLOUD", Set.of("Cloud")));
    candidateRepository.save(candidate("C_TEAM", Set.of("teamwork")));

    SimilarMatches<CandidateEntity> matches = similarityService.findSimilarCandidates("REF",
        candidate -> true);

    assertThat(matches.fallback(), is(true));
    assertThat(candidateIds(matches), contains("C_BS", "C_CLOUD"));
  }

  @Test
  void keepsTheThresholdOfTwoMedianTagsForCandidatesWhenFivePass() {
    for (int i = 0; i < 5; i++) {
      candidateRepository.save(candidate("C_ALL_" + i, Set.of("backend", "sql", "cloud")));
    }
    candidateRepository.save(candidate("C_CLOUD", Set.of("cloud")));

    SimilarMatches<CandidateEntity> matches = similarityService.findSimilarCandidates("REF",
        candidate -> true);

    assertThat(matches.fallback(), is(false));
    assertThat(matches.size(), is(5));
    assertThat(candidateIds(matches), not(hasItem("C_CLOUD")));
  }

  @Test
  void leavesTheReferenceOutOfTheJobPostsSharingItsTags() {
    List<String> sharing = jobIds(jobPostRepository.findSharingTags(
        Set.of("backend", "sql", "cloud", "teamwork"), "REF"));

    assertThat(sharing, hasItem("N5"));
    assertThat(sharing, not(hasItem("REF")));
  }

  private void assertFrequency(String tag, int df, double weight) {
    TagFrequencyEntity frequency = tagFrequencyRepository.findById(tag).orElseThrow();
    assertThat(frequency.getTag(), is(tag));
    assertThat(frequency.getDf(), is(df));
    assertThat(frequency.getWeight(), closeTo(weight, 1e-9));
  }

  private List<String> ids(SimilarMatches<JobPostEntity> matches) {
    return jobIds(matches.items());
  }

  private static List<String> jobIds(List<JobPostEntity> jobPosts) {
    return jobPosts.stream().map(JobPostEntity::getId).toList();
  }

  private static List<String> candidateIds(SimilarMatches<CandidateEntity> matches) {
    return matches.items().stream().map(CandidateEntity::getId).toList();
  }

  private static Map<String, Set<String>> jobPosts() {
    Map<String, Set<String>> jobPosts = new LinkedHashMap<>();
    jobPosts.put("REF", Set.of("backend", "sql", "cloud", "teamwork"));
    jobPosts.put("N1", Set.of("Backend", "sql", "cloud", "teamwork"));
    jobPosts.put("N2", Set.of("backend", "sql", "cloud", "teamwork"));
    jobPosts.put("N3", Set.of("backend", "sql", "cloud", "teamwork"));
    jobPosts.put("N4", Set.of("backend", "sql", "cloud", "teamwork"));
    jobPosts.put("N5", Set.of("backend", "sql", "cloud", "teamwork"));
    jobPosts.put("BROAD", Set.of("backend", "sql", "cloud", "teamwork", "frontend", "mobile",
        "design", "security", "data", "networking"));
    jobPosts.put("HALF", Set.of("backend", "sql", "teamwork"));
    jobPosts.put("CLOUDONLY", Set.of("cloud", "teamwork"));
    jobPosts.put("F1", Set.of("teamwork", "frontend"));
    jobPosts.put("F2", Set.of("teamwork", "design"));
    jobPosts.put("F3", Set.of("teamwork", "mobile"));
    jobPosts.put("RARE", Set.of("teamwork", "kubernetes"));
    return jobPosts;
  }

  private static JobPostEntity jobPost(String id, Set<String> tags) {
    return JobPostEntity.builder()
        .id(id)
        .title(id)
        .workType("REMOTE")
        .source("LYNQ")
        .status(JobStatus.OPEN)
        .publishedOn(LocalDate.parse("2026-09-20"))
        .tags(new HashSet<>(tags))
        .detailsOccurredOn(OCCURRED_ON)
        .statusOccurredOn(OCCURRED_ON)
        .build();
  }

  private static CandidateEntity candidate(String id, Set<String> tags) {
    return CandidateEntity.builder()
        .id(id)
        .tags(new HashSet<>(tags))
        .skillsOccurredOn(OCCURRED_ON)
        .build();
  }
}
