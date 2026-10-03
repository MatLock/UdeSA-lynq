package com.lynq.analytics.repository;

import com.lynq.analytics.model.SkillDailyDemandEntity;
import com.lynq.analytics.model.SkillDailyDemandId;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SkillDailyDemandRepository
    extends JpaRepository<SkillDailyDemandEntity, SkillDailyDemandId> {

  @Query("""
      select d from SkillDailyDemandEntity d
      where d.snapshotOn = :snapshotOn
      order by d.openJobPosts desc, d.skill asc""")
  List<SkillDailyDemandEntity> findMostDemanded(@Param("snapshotOn") LocalDate snapshotOn,
      Pageable pageable);

  List<SkillDailyDemandEntity> findBySnapshotOnAndSkillIn(LocalDate snapshotOn,
      Collection<String> skills);

  @Modifying
  @Query("delete from SkillDailyDemandEntity d where d.snapshotOn = :snapshotOn")
  int deleteSnapshotOf(@Param("snapshotOn") LocalDate snapshotOn);
}
