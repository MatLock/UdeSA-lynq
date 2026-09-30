package com.lynq.analytics.service;

import com.lynq.analytics.config.SimilarityProperties;
import com.lynq.analytics.enums.JobStatus;
import com.lynq.analytics.exceptions.NotFoundException;
import com.lynq.analytics.model.CandidateEntity;
import com.lynq.analytics.model.JobPostEntity;
import com.lynq.analytics.model.TagFrequencyEntity;
import com.lynq.analytics.repository.CandidateRepository;
import com.lynq.analytics.repository.JobPostRepository;
import com.lynq.analytics.similarity.SimilarMatches;
import com.lynq.analytics.similarity.TagWeights;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SimilarityServiceTest {

  private static final String JOB_ID = "77777777-7777-7777-7777-777777777777";
  private static final Set<String> REFERENCE_TAGS = Set.of("Backend", "SQL", "Cloud");

  private static final TagWeights WEIGHTS = TagWeights.of(List.of(
      frequency("backend", 1.0), frequency("sql", 1.0), frequency("cloud", 1.0),
      frequency("teamwork", 0.0)));

  @Mock
  private JobPostRepository jobPostRepository;

  @Mock
  private CandidateRepository candidateRepository;

  @Mock
  private TagFrequencyService tagFrequencyService;

  private SimilarityService similarityService;

  @BeforeEach
  void setUp() {
    similarityService = service(true);
  }

  @Test
  void admitsJobPostsAtTheThresholdOfTwoMedianTagsWhenTheSampleIsLargeEnough() {
    givenReference(REFERENCE_TAGS, Set.of());
    List<JobPostEntity> others = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      others.add(jobPost("same-" + i, Set.of("backend", "sql", "cloud")));
    }
    others.add(jobPost("one-tag", Set.of("cloud")));
    givenJobPostsSharingTags(others);

    SimilarMatches<JobPostEntity> matches = similarityService.findSimilarJobPosts(JOB_ID,
        job -> true);

    assertThat(matches.fallback(), is(false));
    assertThat(matches.size(), is(5));
    assertThat(ids(matches).contains("one-tag"), is(false));
  }

  @Test
  void fallsBackToOneMedianTagWhenFewerThanFivePass() {
    givenReference(REFERENCE_TAGS, Set.of());
    givenJobPostsSharingTags(List.of(
        jobPost("same", Set.of("backend", "sql", "cloud")),
        jobPost("one-tag", Set.of("cloud"))));

    SimilarMatches<JobPostEntity> matches = similarityService.findSimilarJobPosts(JOB_ID,
        job -> true);

    assertThat(matches.fallback(), is(true));
    assertThat(ids(matches), contains("same", "one-tag"));
  }

  @Test
  void countsTheSampleAfterTheEligibilityFilter() {
    givenReference(REFERENCE_TAGS, Set.of());
    List<JobPostEntity> others = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      JobPostEntity same = jobPost("same-" + i, Set.of("backend", "sql", "cloud"));
      same.setStatus(i == 0 ? JobStatus.CLOSE : JobStatus.OPEN);
      others.add(same);
    }
    JobPostEntity oneTag = jobPost("one-tag", Set.of("cloud"));
    oneTag.setStatus(JobStatus.CLOSE);
    others.add(oneTag);
    givenJobPostsSharingTags(others);

    SimilarMatches<JobPostEntity> matches = similarityService.findSimilarJobPosts(JOB_ID,
        job -> job.getStatus() == JobStatus.CLOSE);

    assertThat(matches.fallback(), is(true));
    assertThat(ids(matches), containsInAnyOrder("same-0", "one-tag"));
  }

  @Test
  void aTagThatWeighsNothingAdmitsNobodyEvenOnFallback() {
    givenReference(Set.of("backend", "teamwork"), Set.of());
    givenJobPostsSharingTags(List.of(jobPost("teamwork-only", Set.of("teamwork"))));

    assertThat(similarityService.findSimilarJobPosts(JOB_ID, job -> true).matches(), is(empty()));
  }

  @Test
  void ordersByScoreAndThenBySharedSkills() {
    givenReference(REFERENCE_TAGS, Set.of("Java", "Spring"));
    JobPostEntity oneSkill = jobPost("one-skill", Set.of("backend", "sql", "cloud"));
    oneSkill.setSkills(new HashSet<>(Set.of("java")));
    JobPostEntity twoSkills = jobPost("two-skills", Set.of("backend", "sql", "cloud"));
    twoSkills.setSkills(new HashSet<>(Set.of("Java", "SPRING")));
    JobPostEntity lower = jobPost("lower", Set.of("backend", "sql"));
    givenJobPostsSharingTags(List.of(lower, oneSkill, twoSkills));

    SimilarMatches<JobPostEntity> matches = similarityService.findSimilarJobPosts(JOB_ID,
        job -> true);

    assertThat(ids(matches), contains("two-skills", "one-skill", "lower"));
    assertThat(matches.matches().getFirst().sharedSkills(), is(2));
  }

  @Test
  void aReferenceWithoutTagsHasNoSimilarJobPosts() {
    givenReference(Set.of(), Set.of());

    SimilarMatches<JobPostEntity> matches = similarityService.findSimilarJobPosts(JOB_ID,
        job -> true);

    assertThat(matches.matches(), is(empty()));
    verifyNoInteractions(tagFrequencyService);
  }

  @Test
  void anUnknownJobPostIsNotFound() {
    when(jobPostRepository.findById(JOB_ID)).thenReturn(Optional.empty());

    assertThrows(NotFoundException.class,
        () -> similarityService.findSimilarJobPosts(JOB_ID, job -> true));
  }

  @Test
  void passesTheSyntheticSettingToTheQuery() {
    similarityService = service(false);
    givenReference(REFERENCE_TAGS, Set.of());
    when(jobPostRepository.findSharingTags(any(), eq(JOB_ID), eq(false))).thenReturn(List.of());
    when(tagFrequencyService.weights()).thenReturn(WEIGHTS);

    similarityService.findSimilarJobPosts(JOB_ID, job -> true);

    verify(jobPostRepository).findSharingTags(Set.of("backend", "sql", "cloud"), JOB_ID, false);
  }

  @Test
  void admitsCandidatesByTheWeightTheyShareWithTheJobPost() {
    givenReference(REFERENCE_TAGS, Set.of());
    List<CandidateEntity> candidates = new ArrayList<>();
    for (int i = 0; i < 5; i++) {
      candidates.add(candidate("two-tags-" + i, Set.of("backend", "sql", "design", "mobile")));
    }
    candidates.add(candidate("one-tag", Set.of("cloud")));
    when(candidateRepository.findSharingTags(Set.of("backend", "sql", "cloud"), true))
        .thenReturn(candidates);
    when(tagFrequencyService.weights()).thenReturn(WEIGHTS);

    SimilarMatches<CandidateEntity> matches = similarityService.findSimilarCandidates(JOB_ID,
        candidate -> true);

    assertThat(matches.fallback(), is(false));
    assertThat(matches.threshold(), is(2.0));
    assertThat(matches.size(), is(5));
    assertThat(matches.matches().getFirst().score(), is(2.0));
  }

  @Test
  void fallsBackForCandidatesWhenFewerThanFivePass() {
    givenReference(REFERENCE_TAGS, Set.of());
    when(candidateRepository.findSharingTags(any(), anyBoolean())).thenReturn(List.of(
        candidate("two-tags", Set.of("backend", "sql")),
        candidate("one-tag", Set.of("cloud")),
        candidate("teamwork-only", Set.of("teamwork"))));
    when(tagFrequencyService.weights()).thenReturn(WEIGHTS);

    SimilarMatches<CandidateEntity> matches = similarityService.findSimilarCandidates(JOB_ID,
        candidate -> true);

    assertThat(matches.fallback(), is(true));
    assertThat(matches.threshold(), is(1.0));
    assertThat(matches.items().stream().map(CandidateEntity::getId).toList(),
        contains("two-tags", "one-tag"));
  }

  @Test
  void filtersCandidatesByEligibility() {
    givenReference(REFERENCE_TAGS, Set.of());
    CandidateEntity withSalary = candidate("with-salary", Set.of("backend", "sql"));
    withSalary.setExpectedSalary(2000000);
    when(candidateRepository.findSharingTags(any(), anyBoolean())).thenReturn(List.of(
        withSalary, candidate("without-salary", Set.of("backend", "sql"))));
    when(tagFrequencyService.weights()).thenReturn(WEIGHTS);

    SimilarMatches<CandidateEntity> matches = similarityService.findSimilarCandidates(JOB_ID,
        candidate -> candidate.getExpectedSalary() != null);

    assertThat(matches.items(), contains(withSalary));
  }

  private SimilarityService service(boolean includeSynthetic) {
    return new SimilarityService(jobPostRepository, candidateRepository, tagFrequencyService,
        new SimilarityProperties(includeSynthetic, 2, 1, 5));
  }

  private void givenReference(Set<String> tags, Set<String> skills) {
    JobPostEntity reference = jobPost(JOB_ID, tags);
    reference.setSkills(new HashSet<>(skills));
    when(jobPostRepository.findById(JOB_ID)).thenReturn(Optional.of(reference));
  }

  private void givenJobPostsSharingTags(List<JobPostEntity> others) {
    when(jobPostRepository.findSharingTags(any(), eq(JOB_ID), eq(true))).thenReturn(others);
    when(tagFrequencyService.weights()).thenReturn(WEIGHTS);
  }

  private static List<String> ids(SimilarMatches<JobPostEntity> matches) {
    return matches.items().stream().map(JobPostEntity::getId).toList();
  }

  private static JobPostEntity jobPost(String id, Set<String> tags) {
    return JobPostEntity.builder().id(id).status(JobStatus.OPEN).tags(new HashSet<>(tags)).build();
  }

  private static CandidateEntity candidate(String id, Set<String> tags) {
    return CandidateEntity.builder().id(id).tags(new HashSet<>(tags)).build();
  }

  private static TagFrequencyEntity frequency(String tag, double weight) {
    return TagFrequencyEntity.builder().tag(tag).df(1).weight(weight).build();
  }
}
