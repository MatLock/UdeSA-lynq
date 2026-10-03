package com.lynq.analytics.repository;

import com.lynq.analytics.model.CategoryDailySalaryEntity;
import com.lynq.analytics.model.CategoryDailySalaryId;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CategoryDailySalaryRepository
    extends JpaRepository<CategoryDailySalaryEntity, CategoryDailySalaryId> {

  List<CategoryDailySalaryEntity> findBySnapshotOnAndCurrencyOrderByCategoryAscWorkTypeAsc(
      LocalDate snapshotOn, String currency);

  @Modifying
  @Query("delete from CategoryDailySalaryEntity s where s.snapshotOn = :snapshotOn")
  int deleteSnapshotOf(@Param("snapshotOn") LocalDate snapshotOn);
}
