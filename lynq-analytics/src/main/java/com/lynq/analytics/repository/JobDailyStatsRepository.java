package com.lynq.analytics.repository;

import com.lynq.analytics.model.JobDailyStatsEntity;
import com.lynq.analytics.model.JobDailyStatsId;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface JobDailyStatsRepository
    extends JpaRepository<JobDailyStatsEntity, JobDailyStatsId> {

  @Query("select max(s.snapshotOn) from JobDailyStatsEntity s")
  Optional<LocalDate> findLatestSnapshotOn();

  List<JobDailyStatsEntity> findBySnapshotOn(LocalDate snapshotOn);

  boolean existsBySnapshotOn(LocalDate snapshotOn);

  @Modifying
  @Query("delete from JobDailyStatsEntity s where s.snapshotOn = :snapshotOn")
  int deleteSnapshotOf(@Param("snapshotOn") LocalDate snapshotOn);
}
