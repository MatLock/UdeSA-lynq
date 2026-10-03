package com.lynq.analytics.repository;

import com.lynq.analytics.model.CandidateDailyBenchmarkEntity;
import com.lynq.analytics.model.CandidateDailyBenchmarkId;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CandidateDailyBenchmarkRepository
    extends JpaRepository<CandidateDailyBenchmarkEntity, CandidateDailyBenchmarkId> {

  Optional<CandidateDailyBenchmarkEntity> findFirstByCandidateIdOrderBySnapshotOnDesc(
      String candidateId);

  List<CandidateDailyBenchmarkEntity> findByCandidateIdAndSnapshotOnGreaterThanEqualOrderBySnapshotOnAsc(
      String candidateId, LocalDate from);

  @Modifying
  @Query(value = "DELETE FROM candidate_daily_skill_unlocks WHERE snapshot_on = :snapshotOn",
      nativeQuery = true)
  int deleteSkillUnlocksOf(@Param("snapshotOn") LocalDate snapshotOn);

  @Modifying
  @Query(value = "DELETE FROM candidate_daily_benchmark WHERE snapshot_on = :snapshotOn",
      nativeQuery = true)
  int deleteBenchmarksOf(@Param("snapshotOn") LocalDate snapshotOn);
}
