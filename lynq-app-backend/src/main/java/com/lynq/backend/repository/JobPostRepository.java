package com.lynq.backend.repository;

import com.lynq.backend.model.JobPostEntity;
import com.lynq.backend.repository.projection.JobWithDetailsProjection;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface JobPostRepository extends JpaRepository<JobPostEntity, String> {

  @Modifying(clearAutomatically = true)
  @Query("UPDATE JobPostEntity j SET j.totalSeen = j.totalSeen + 1 WHERE j.id = :jobId")
  int increaseTotalSeen(@Param("jobId") String jobId);

  @Query(value = "SELECT new com.lynq.backend.repository.projection.JobWithDetailsProjection("
      + "j.id, j.title, j.description, j.workType, j.salaryRangeDown, j.salaryRangeTop, j.salaryCurrency, j.jobUrl, "
      + "j.jobPostSource, j.createdOn, j.totalSeen, j.jobStatus, "
      + "c.id, c.name, c.about, c.size, c.lynqFileStorageId, c.logoUrl, "
      + "u.id, u.fullName, u.lynqFileStorageId, u.currentPosition, "
      + "CAST((SELECT function('group_concat', sk.skill) FROM JobPostSkillEntity sk "
      + "WHERE sk.jobPost = j) AS string), "
      + "CAST((SELECT function('group_concat', tg.similarityTag) FROM JobPostSimilarityTagEntity tg "
      + "WHERE tg.jobPost = j) AS string)) "
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
  Page<JobWithDetailsProjection> searchAvailableJobs(@Param("filterValue") String filterValue, Pageable pageable);

  @Query("SELECT new com.lynq.backend.repository.projection.JobWithDetailsProjection("
      + "j.id, j.title, j.description, j.workType, j.salaryRangeDown, j.salaryRangeTop, j.salaryCurrency, j.jobUrl, "
      + "j.jobPostSource, j.createdOn, j.totalSeen, j.jobStatus, "
      + "c.id, c.name, c.about, c.size, c.lynqFileStorageId, c.logoUrl, "
      + "u.id, u.fullName, u.lynqFileStorageId, u.currentPosition, "
      + "CAST((SELECT function('group_concat', sk.skill) FROM JobPostSkillEntity sk "
      + "WHERE sk.jobPost = j) AS string), "
      + "CAST((SELECT function('group_concat', tg.similarityTag) FROM JobPostSimilarityTagEntity tg "
      + "WHERE tg.jobPost = j) AS string)) "
      + "FROM JobPostEntity j "
      + "LEFT JOIN j.company c "
      + "LEFT JOIN j.createdByUser u "
      + "WHERE j.id = :jobId")
  Optional<JobWithDetailsProjection> findJobDetailsById(@Param("jobId") String jobId);

  @Query(value = "SELECT new com.lynq.backend.repository.projection.JobWithDetailsProjection("
      + "j.id, j.title, j.description, j.workType, j.salaryRangeDown, j.salaryRangeTop, j.salaryCurrency, j.jobUrl, "
      + "j.jobPostSource, j.createdOn, j.totalSeen, j.jobStatus, "
      + "c.id, c.name, c.about, c.size, c.lynqFileStorageId, c.logoUrl, "
      + "u.id, u.fullName, u.lynqFileStorageId, u.currentPosition, "
      + "CAST((SELECT function('group_concat', sk.skill) FROM JobPostSkillEntity sk "
      + "WHERE sk.jobPost = j) AS string), "
      + "CAST((SELECT function('group_concat', tg.similarityTag) FROM JobPostSimilarityTagEntity tg "
      + "WHERE tg.jobPost = j) AS string)) "
      + "FROM JobPostEntity j "
      + "LEFT JOIN j.company c "
      + "LEFT JOIN j.createdByUser u "
      + "WHERE u.id = :userId "
      + "ORDER BY j.createdOn DESC",
      countQuery = "SELECT COUNT(j) FROM JobPostEntity j "
      + "WHERE j.createdByUser.id = :userId")
  Page<JobWithDetailsProjection> searchJobsOwnedByUser(@Param("userId") String userId,
      Pageable pageable);

  @Query("SELECT j FROM JobPostEntity j WHERE j.createdByUser.id = :userId ORDER BY j.createdOn DESC")
  List<JobPostEntity> findByCreatedByUserId(@Param("userId") String userId);

  @Query("SELECT j FROM JobPostEntity j WHERE j.company.id = :companyId ORDER BY j.createdOn DESC")
  List<JobPostEntity> findByCompanyId(@Param("companyId") String companyId);

  @Query(value = """
      SELECT ranked.id FROM (
        SELECT j.id, j.category,
          ROW_NUMBER() OVER (
            PARTITION BY j.category
            ORDER BY CASE WHEN j.last_checked_on IS NULL THEN 0 ELSE 1 END,
              j.last_checked_on, j.id) AS position
        FROM job_posts j
        WHERE j.job_post_source <> 'LYNQ'
          AND j.job_status = 'OPEN'
          AND j.job_url IS NOT NULL
          AND COALESCE(j.last_seen_on, j.created_on) < :seenBefore
          AND (j.last_checked_on IS NULL OR j.last_checked_on < :checkedBefore)
      ) ranked
      WHERE ranked.position <= :quota
      ORDER BY ranked.position, ranked.category, ranked.id""", nativeQuery = true)
  List<String> findVerificationCandidateIds(@Param("seenBefore") LocalDate seenBefore,
      @Param("checkedBefore") LocalDate checkedBefore, @Param("quota") int quota);

  @Query("""
      SELECT j FROM JobPostEntity j
      WHERE j.jobPostSource <> com.lynq.backend.enums.JobPostSource.LYNQ
        AND j.jobStatus = com.lynq.backend.enums.JobStatus.OPEN
        AND COALESCE(j.lastSeenOn, j.createdOn) < :seenBefore""")
  List<JobPostEntity> findOpenExternalNotSeenSince(@Param("seenBefore") LocalDate seenBefore);
}
