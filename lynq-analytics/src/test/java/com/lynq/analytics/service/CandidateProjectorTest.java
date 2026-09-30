package com.lynq.analytics.service;

import com.lynq.analytics.exceptions.InvalidDomainEventException;
import com.lynq.analytics.listener.message.DomainEventMessage;
import com.lynq.analytics.model.CandidateEntity;
import com.lynq.analytics.repository.CandidateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static com.lynq.analytics.service.CandidateProjector.CANDIDATE_EXPECTED_SALARY_UPDATED;
import static com.lynq.analytics.service.CandidateProjector.CANDIDATE_SKILLS_UPDATED;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CandidateProjectorTest {

  private static final JsonMapper JSON = JsonMapper.builder().build();

  private static final UUID EVENT_ID = UUID.fromString("5c8f3a3e-0b7e-5d61-9c1a-2f4b8e6d7a10");
  private static final String USER_ID = "11111111-1111-1111-1111-111111111111";
  private static final Instant T1 = Instant.parse("2026-09-20T10:00:00Z");
  private static final Instant T2 = Instant.parse("2026-09-22T10:00:00Z");
  private static final Instant T3 = Instant.parse("2026-09-24T10:00:00Z");

  private static final String SKILLS_UPDATED = """
      {"userId": "%s", "skills": ["Java", " Spring ", "java", ""],
       "similarityTags": ["backend", "jvm", "Backend"], "synthetic": true,
       "notYetKnown": "ignored"}""".formatted(USER_ID);

  private static final String SALARY_UPDATED = """
      {"userId": "%s", "expectedSalary": 2000000, "currency": "ARS"}""".formatted(USER_ID);

  @Mock
  private CandidateRepository candidateRepository;

  private CandidateProjector candidateProjector;

  @BeforeEach
  void setUp() {
    candidateProjector = new CandidateProjector(candidateRepository, JSON);
  }

  @Test
  void supportsTheTwoCandidateEvents() {
    assertThat(candidateProjector.supports(CANDIDATE_SKILLS_UPDATED), is(true));
    assertThat(candidateProjector.supports(CANDIDATE_EXPECTED_SALARY_UPDATED), is(true));
    assertThat(candidateProjector.supports("ApplicationSubmitted"), is(false));
  }

  @Test
  void skillsUpdatedCreatesTheCandidateWithItsSkillsAndTags() {
    when(candidateRepository.findById(USER_ID)).thenReturn(Optional.empty());

    candidateProjector.project(message(CANDIDATE_SKILLS_UPDATED, T1, SKILLS_UPDATED));

    CandidateEntity candidate = saved();
    assertThat(candidate.getId(), is(USER_ID));
    assertThat(candidate.getSkills(), containsInAnyOrder("Java", "Spring"));
    assertThat(candidate.getTags(), containsInAnyOrder("backend", "jvm"));
    assertThat(candidate.isSynthetic(), is(true));
    assertThat(candidate.getSkillsOccurredOn(), is(T1));
    assertThat(candidate.getSalaryOccurredOn(), is(nullValue()));
    assertThat(candidate.getExpectedSalary(), is(nullValue()));
  }

  @Test
  void skillsUpdatedReplacesTheStoredSkillsAndTagsAndKeepsTheSalary() {
    CandidateEntity candidate = storedCandidate(T1, T1);
    when(candidateRepository.findById(USER_ID)).thenReturn(Optional.of(candidate));

    candidateProjector.project(message(CANDIDATE_SKILLS_UPDATED, T2, """
        {"userId": "%s", "skills": ["Kotlin"], "similarityTags": null}""".formatted(USER_ID)));

    assertThat(saved(), is(sameInstance(candidate)));
    assertThat(candidate.getSkills(), containsInAnyOrder("Kotlin"));
    assertThat(candidate.getTags(), is(empty()));
    assertThat(candidate.isSynthetic(), is(false));
    assertThat(candidate.getSkillsOccurredOn(), is(T2));
    assertThat(candidate.getExpectedSalary(), is(1500000));
    assertThat(candidate.getSalaryOccurredOn(), is(T1));
  }

  @Test
  void skillsUpdatedOlderThanTheStoredSkillsIsDiscarded() {
    CandidateEntity candidate = storedCandidate(T3, T1);
    when(candidateRepository.findById(USER_ID)).thenReturn(Optional.of(candidate));

    candidateProjector.project(message(CANDIDATE_SKILLS_UPDATED, T2, SKILLS_UPDATED));

    verify(candidateRepository, never()).save(any());
    assertThat(candidate.getSkills(), containsInAnyOrder("Cobol"));
  }

  @Test
  void skillsUpdatedIsAppliedEvenWhenANewerSalaryWasAlreadyProjected() {
    CandidateEntity candidate = storedCandidate(T1, T3);
    when(candidateRepository.findById(USER_ID)).thenReturn(Optional.of(candidate));

    candidateProjector.project(message(CANDIDATE_SKILLS_UPDATED, T2, SKILLS_UPDATED));

    assertThat(saved().getSkills(), containsInAnyOrder("Java", "Spring"));
  }

  @Test
  void expectedSalaryUpdatedCreatesTheCandidateWithItsSalary() {
    when(candidateRepository.findById(USER_ID)).thenReturn(Optional.empty());

    candidateProjector.project(message(CANDIDATE_EXPECTED_SALARY_UPDATED, T1, SALARY_UPDATED));

    CandidateEntity candidate = saved();
    assertThat(candidate.getId(), is(USER_ID));
    assertThat(candidate.getExpectedSalary(), is(2000000));
    assertThat(candidate.getExpectedSalaryCurrency(), is("ARS"));
    assertThat(candidate.isSynthetic(), is(false));
    assertThat(candidate.getSalaryOccurredOn(), is(T1));
    assertThat(candidate.getSkillsOccurredOn(), is(nullValue()));
    assertThat(candidate.getSkills(), is(empty()));
  }

  @Test
  void expectedSalaryUpdatedReplacesTheStoredSalaryAndKeepsTheSkills() {
    CandidateEntity candidate = storedCandidate(T1, T1);
    when(candidateRepository.findById(USER_ID)).thenReturn(Optional.of(candidate));

    candidateProjector.project(message(CANDIDATE_EXPECTED_SALARY_UPDATED, T2, SALARY_UPDATED));

    assertThat(saved(), is(sameInstance(candidate)));
    assertThat(candidate.getExpectedSalary(), is(2000000));
    assertThat(candidate.getSalaryOccurredOn(), is(T2));
    assertThat(candidate.getSkills(), containsInAnyOrder("Cobol"));
    assertThat(candidate.getSkillsOccurredOn(), is(T1));
  }

  @Test
  void expectedSalaryUpdatedWithoutSalaryClearsItAndItsCurrency() {
    CandidateEntity candidate = storedCandidate(T1, T1);
    when(candidateRepository.findById(USER_ID)).thenReturn(Optional.of(candidate));

    candidateProjector.project(message(CANDIDATE_EXPECTED_SALARY_UPDATED, T2, """
        {"userId": "%s", "expectedSalary": null, "currency": "ARS"}""".formatted(USER_ID)));

    assertThat(saved().getExpectedSalary(), is(nullValue()));
    assertThat(candidate.getExpectedSalaryCurrency(), is(nullValue()));
  }

  @Test
  void expectedSalaryUpdatedOlderThanTheStoredSalaryIsDiscarded() {
    CandidateEntity candidate = storedCandidate(T1, T3);
    when(candidateRepository.findById(USER_ID)).thenReturn(Optional.of(candidate));

    candidateProjector.project(message(CANDIDATE_EXPECTED_SALARY_UPDATED, T2, SALARY_UPDATED));

    verify(candidateRepository, never()).save(any());
    assertThat(candidate.getExpectedSalary(), is(1500000));
  }

  @Test
  void expectedSalaryUpdatedAtTheSameInstantAsTheStoredSalaryIsApplied() {
    CandidateEntity candidate = storedCandidate(T1, T2);
    when(candidateRepository.findById(USER_ID)).thenReturn(Optional.of(candidate));

    candidateProjector.project(message(CANDIDATE_EXPECTED_SALARY_UPDATED, T2, SALARY_UPDATED));

    assertThat(saved().getExpectedSalary(), is(2000000));
  }

  @Test
  void rejectsAPayloadWhoseUserIdIsNotTheAggregateId() {
    assertRejected(message(CANDIDATE_SKILLS_UPDATED, T1, """
            {"userId": "99999999-9999-9999-9999-999999999999", "skills": ["Java"]}"""),
        "has userId '99999999-9999-9999-9999-999999999999' but aggregateId '" + USER_ID + "'");
  }

  @Test
  void rejectsAPayloadWithoutUserId() {
    assertRejected(message(CANDIDATE_EXPECTED_SALARY_UPDATED, T1, """
        {"expectedSalary": 2000000, "currency": "ARS"}"""), "has no userId");
  }

  @Test
  void rejectsAnExpectedSalaryWithoutCurrency() {
    assertRejected(message(CANDIDATE_EXPECTED_SALARY_UPDATED, T1, """
        {"userId": "%s", "expectedSalary": 2000000}""".formatted(USER_ID)), "has no currency");
  }

  @Test
  void rejectsAnExpectedSalaryThatIsNotPositive() {
    assertRejected(message(CANDIDATE_EXPECTED_SALARY_UPDATED, T1, """
            {"userId": "%s", "expectedSalary": 0, "currency": "ARS"}""".formatted(USER_ID)),
        "has expectedSalary 0, which is not positive");
  }

  @Test
  void rejectsAPayloadThatCannotBeRead() {
    InvalidDomainEventException exception = assertThrows(InvalidDomainEventException.class,
        () -> candidateProjector.project(message(CANDIDATE_EXPECTED_SALARY_UPDATED, T1, """
            {"userId": "%s", "expectedSalary": "a lot"}""".formatted(USER_ID))));

    assertThat(exception.getMessage().startsWith("Domain event '" + EVENT_ID + "' of type '"
        + CANDIDATE_EXPECTED_SALARY_UPDATED + "' has a payload that cannot be read: "), is(true));
    verifyNoInteractions(candidateRepository);
  }

  @Test
  void refusesAnEventTypeItDoesNotSupport() {
    assertThrows(IllegalArgumentException.class,
        () -> candidateProjector.project(message("ApplicationSubmitted", T1, SALARY_UPDATED)));
  }

  private void assertRejected(DomainEventMessage message, String problem) {
    InvalidDomainEventException exception = assertThrows(InvalidDomainEventException.class,
        () -> candidateProjector.project(message));

    assertThat(exception.getMessage(), is("Domain event '" + EVENT_ID + "' of type '"
        + message.eventType() + "' " + problem));
    verifyNoInteractions(candidateRepository);
  }

  private CandidateEntity saved() {
    ArgumentCaptor<CandidateEntity> captor = ArgumentCaptor.forClass(CandidateEntity.class);
    verify(candidateRepository).save(captor.capture());
    return captor.getValue();
  }

  private CandidateEntity storedCandidate(Instant skillsOccurredOn, Instant salaryOccurredOn) {
    return CandidateEntity.builder()
        .id(USER_ID)
        .expectedSalary(1500000)
        .expectedSalaryCurrency("ARS")
        .skills(new HashSet<>(Set.of("Cobol")))
        .tags(new HashSet<>(Set.of("mainframe")))
        .skillsOccurredOn(skillsOccurredOn)
        .salaryOccurredOn(salaryOccurredOn)
        .build();
  }

  private DomainEventMessage message(String eventType, Instant occurredOn, String payload) {
    return new DomainEventMessage(EVENT_ID, eventType, "CANDIDATE", USER_ID, occurredOn,
        JSON.readTree(payload));
  }
}
