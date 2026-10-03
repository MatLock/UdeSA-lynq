package com.lynq.analytics.service;

import com.lynq.analytics.config.SalaryProperties;
import com.lynq.analytics.exceptions.NotFoundException;
import com.lynq.analytics.model.CandidateEntity;
import com.lynq.analytics.model.JobPostEntity;
import com.lynq.analytics.repository.JobPostRepository;
import com.lynq.analytics.similarity.SimilarMatch;
import com.lynq.analytics.similarity.SimilarMatches;
import com.lynq.analytics.stats.SalaryInsights;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SalaryServiceTest {

  private static final String JOB_ID = "77777777-7777-7777-7777-777777777777";

  @Mock
  private JobPostRepository jobPostRepository;

  @Mock
  private SimilarityService similarityService;

  private SalaryService salaryService;

  @BeforeEach
  void setUp() {
    salaryService = new SalaryService(jobPostRepository, similarityService,
        new SalaryProperties(5, "ARS"));
  }

  @Test
  void summarisesTheSalariesOfSimilarJobPostsAtTheMiddleOfTheirRange() {
    givenReference("USD");
    givenSimilarJobPosts(post(100, 200), post(200, null), post(null, 300), post(400, 400),
        post(500, 700));
    givenSimilarCandidates();

    SalaryInsights insights = salaryService.salary(JOB_ID);

    assertThat(insights.positionSalary().median(), is(300.0));
    assertThat(insights.positionSalary().p25(), is(200.0));
    assertThat(insights.positionSalary().p75(), is(400.0));
    assertThat(insights.positionSalary().n(), is(5));
    assertThat(insights.positionSalary().currency(), is("USD"));
    assertThat(insights.positionSalary().insufficientData(), is(false));
  }

  @Test
  void summarisesTheExpectedSalaryOfSimilarCandidates() {
    givenReference("ARS");
    givenSimilarJobPosts();
    givenSimilarCandidates(candidate(1000), candidate(3000), candidate(2000), candidate(5000),
        candidate(4000));

    SalaryInsights insights = salaryService.salary(JOB_ID);

    assertThat(insights.peersExpectedSalary().median(), is(3000.0));
    assertThat(insights.peersExpectedSalary().n(), is(5));
    assertThat(insights.peersExpectedSalary().currency(), is("ARS"));
    assertThat(insights.positionSalary().n(), is(0));
    assertThat(insights.positionSalary().insufficientData(), is(true));
  }

  @Test
  void withholdsTheStatisticsOfASampleBelowFive() {
    givenReference("ARS");
    givenSimilarJobPosts();
    givenSimilarCandidates(candidate(1000), candidate(2000));

    SalaryInsights insights = salaryService.salary(JOB_ID);

    assertThat(insights.peersExpectedSalary().median(), is(nullValue()));
    assertThat(insights.peersExpectedSalary().n(), is(2));
    assertThat(insights.peersExpectedSalary().insufficientData(), is(true));
  }

  @Test
  void admitsOnlyJobPostsWithASalaryInTheCurrencyOfTheReference() {
    givenReference("USD");
    ArgumentCaptor<Predicate<JobPostEntity>> eligible = givenSimilarJobPosts();
    givenSimilarCandidates();

    salaryService.salary(JOB_ID);

    assertThat(eligible.getValue().test(post(100, 200, "USD")), is(true));
    assertThat(eligible.getValue().test(post(null, 200, "USD")), is(true));
    assertThat(eligible.getValue().test(post(100, 200, "ARS")), is(false));
    assertThat(eligible.getValue().test(post(null, null, "USD")), is(false));
    assertThat(eligible.getValue().test(post(100, 200, null)), is(false));
  }

  @Test
  void admitsOnlyCandidatesWithAnExpectedSalaryInTheCurrencyOfTheReference() {
    givenReference("USD");
    givenSimilarJobPosts();
    ArgumentCaptor<Predicate<CandidateEntity>> eligible = givenSimilarCandidates();

    salaryService.salary(JOB_ID);

    assertThat(eligible.getValue().test(candidate(3000, "USD")), is(true));
    assertThat(eligible.getValue().test(candidate(3000, "ARS")), is(false));
    assertThat(eligible.getValue().test(candidate(null, null)), is(false));
  }

  @Test
  void usesTheDefaultCurrencyForAJobPostWithoutASalary() {
    givenReference(null);
    ArgumentCaptor<Predicate<JobPostEntity>> eligible = givenSimilarJobPosts();
    givenSimilarCandidates();

    SalaryInsights insights = salaryService.salary(JOB_ID);

    assertThat(insights.positionSalary().currency(), is("ARS"));
    assertThat(insights.peersExpectedSalary().currency(), is("ARS"));
    assertThat(eligible.getValue().test(post(100, 200, "ARS")), is(true));
  }

  @Test
  void failsWithNotFoundForAnUnknownJobPost() {
    when(jobPostRepository.findById(JOB_ID)).thenReturn(Optional.empty());

    NotFoundException ex = assertThrows(NotFoundException.class,
        () -> salaryService.salary(JOB_ID));

    assertThat(ex.getMessage(), is("Job post '" + JOB_ID + "' not found"));
    verifyNoInteractions(similarityService);
  }

  private void givenReference(String currency) {
    when(jobPostRepository.findById(JOB_ID))
        .thenReturn(Optional.of(post(1000, 2000, currency)));
  }

  @SuppressWarnings("unchecked")
  private ArgumentCaptor<Predicate<JobPostEntity>> givenSimilarJobPosts(JobPostEntity... posts) {
    ArgumentCaptor<Predicate<JobPostEntity>> eligible = ArgumentCaptor.forClass(Predicate.class);
    when(similarityService.findSimilarJobPosts(eq(JOB_ID), eligible.capture()))
        .thenReturn(matches(posts));
    return eligible;
  }

  @SuppressWarnings("unchecked")
  private ArgumentCaptor<Predicate<CandidateEntity>> givenSimilarCandidates(
      CandidateEntity... candidates) {
    ArgumentCaptor<Predicate<CandidateEntity>> eligible = ArgumentCaptor.forClass(Predicate.class);
    when(similarityService.findSimilarCandidates(eq(JOB_ID), eligible.capture()))
        .thenReturn(matches(candidates));
    return eligible;
  }

  @SafeVarargs
  private static <T> SimilarMatches<T> matches(T... items) {
    return new SimilarMatches<>(List.of(items).stream()
        .map(item -> new SimilarMatch<>(item, 1.0, 0))
        .toList(), 0.5, false);
  }

  private static JobPostEntity post(Integer down, Integer top) {
    return post(down, top, "ARS");
  }

  private static JobPostEntity post(Integer down, Integer top, String currency) {
    return JobPostEntity.builder()
        .id(JOB_ID)
        .salaryRangeDown(down)
        .salaryRangeTop(top)
        .salaryCurrency(currency)
        .build();
  }

  private static CandidateEntity candidate(Integer expectedSalary) {
    return candidate(expectedSalary, "ARS");
  }

  private static CandidateEntity candidate(Integer expectedSalary, String currency) {
    return CandidateEntity.builder()
        .id("11111111-1111-1111-1111-111111111111")
        .expectedSalary(expectedSalary)
        .expectedSalaryCurrency(currency)
        .build();
  }
}
