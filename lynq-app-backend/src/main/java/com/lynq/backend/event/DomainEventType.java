package com.lynq.backend.event;

public enum DomainEventType {
  JOB_POST_PUBLISHED("JobPostPublished", AggregateType.JOB_POST),
  JOB_POST_UPDATED("JobPostUpdated", AggregateType.JOB_POST),
  JOB_POST_CLOSED("JobPostClosed", AggregateType.JOB_POST),
  JOB_POST_REOPENED("JobPostReopened", AggregateType.JOB_POST),
  APPLICATION_SUBMITTED("ApplicationSubmitted", AggregateType.APPLICATION),
  CANDIDATE_SKILLS_UPDATED("CandidateSkillsUpdated", AggregateType.CANDIDATE),
  CANDIDATE_EXPECTED_SALARY_UPDATED("CandidateExpectedSalaryUpdated", AggregateType.CANDIDATE);

  private final String eventName;
  private final AggregateType aggregateType;

  DomainEventType(String eventName, AggregateType aggregateType) {
    this.eventName = eventName;
    this.aggregateType = aggregateType;
  }

  public String eventName() {
    return eventName;
  }

  public AggregateType aggregateType() {
    return aggregateType;
  }
}
