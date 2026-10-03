package com.lynq.analytics.service;

import com.lynq.analytics.model.CandidateDailyBenchmarkEntity;
import com.lynq.analytics.repository.CandidateDailyBenchmarkRepository;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class CandidateBenchmarkStore {

  private final CandidateDailyBenchmarkRepository candidateDailyBenchmarkRepository;

  public CandidateBenchmarkStore(
      CandidateDailyBenchmarkRepository candidateDailyBenchmarkRepository) {
    this.candidateDailyBenchmarkRepository = candidateDailyBenchmarkRepository;
  }

  @Transactional
  public void replace(LocalDate snapshotOn, List<CandidateDailyBenchmarkEntity> rows) {
    candidateDailyBenchmarkRepository.deleteSkillUnlocksOf(snapshotOn);
    candidateDailyBenchmarkRepository.deleteBenchmarksOf(snapshotOn);
    candidateDailyBenchmarkRepository.saveAll(rows);
  }
}
