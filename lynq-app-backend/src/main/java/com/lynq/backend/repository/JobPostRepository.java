package com.lynq.backend.repository;

import com.lynq.backend.model.JobPostEntity;
import com.lynq.backend.repository.projection.JobWithDetailsProjection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface JobPostRepository extends JpaRepository<JobPostEntity, String> {

  @Query(value = "SELECT new com.lynq.backend.repository.projection.JobWithDetailsProjection("
      + "j.id, j.title, j.description, j.workType, j.salaryRangeDown, j.salaryRangeTop, j.jobUrl, "
      + "j.jobPostSource, j.createdOn, "
      + "c.id, c.name, c.about, c.size, c.profileImageUrl, "
      + "u.id, u.fullName, u.profileImageUrl, u.currentPosition, "
      + "CAST((SELECT function('group_concat', sk.skill) FROM JobPostSkillEntity sk "
      + "WHERE sk.jobPost = j) AS string)) "
      + "FROM JobPostEntity j "
      + "LEFT JOIN j.company c "
      + "LEFT JOIN j.createdByUser u "
      + "WHERE j.jobStatus = com.lynq.backend.enums.JobStatus.OPEN "
      + "AND (:filterValue IS NULL OR ("
      + "LOWER(j.title) LIKE LOWER(CONCAT('%', :filterValue, '%')) "
      + "OR LOWER(j.description) LIKE LOWER(CONCAT('%', :filterValue, '%')) "
      + "OR LOWER(c.name) LIKE LOWER(CONCAT('%', :filterValue, '%')) "
      + "OR LOWER(CAST(j.workType AS string)) LIKE LOWER(CONCAT('%', :filterValue, '%')) "
      + "OR EXISTS (SELECT 1 FROM JobPostSkillEntity s "
      + "WHERE s.jobPost = j AND LOWER(s.skill) LIKE LOWER(CONCAT('%', :filterValue, '%'))))) "
      + "ORDER BY j.createdOn DESC",
      countQuery = "SELECT COUNT(j) FROM JobPostEntity j "
      + "LEFT JOIN j.company c "
      + "LEFT JOIN j.createdByUser u "
      + "WHERE j.jobStatus = com.lynq.backend.enums.JobStatus.OPEN "
      + "AND (:filterValue IS NULL OR ("
      + "LOWER(j.title) LIKE LOWER(CONCAT('%', :filterValue, '%')) "
      + "OR LOWER(j.description) LIKE LOWER(CONCAT('%', :filterValue, '%')) "
      + "OR LOWER(c.name) LIKE LOWER(CONCAT('%', :filterValue, '%')) "
      + "OR LOWER(CAST(j.workType AS string)) LIKE LOWER(CONCAT('%', :filterValue, '%')) "
      + "OR EXISTS (SELECT 1 FROM JobPostSkillEntity s "
      + "WHERE s.jobPost = j AND LOWER(s.skill) LIKE LOWER(CONCAT('%', :filterValue, '%')))))")
  Page<JobWithDetailsProjection> searchAvailableJobs(
      @Param("filterValue") String filterValue,
      Pageable pageable);

}