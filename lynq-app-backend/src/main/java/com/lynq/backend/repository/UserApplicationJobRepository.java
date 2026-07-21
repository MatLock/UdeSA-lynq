package com.lynq.backend.repository;

import com.lynq.backend.model.UserApplicationJobEntity;
import com.lynq.backend.repository.projection.JobCandidateProjection;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface UserApplicationJobRepository extends JpaRepository<UserApplicationJobEntity, String> {

  @Query("SELECT COUNT(a) > 0 FROM UserApplicationJobEntity a "
      + "WHERE a.jobPost.id = :jobId AND a.user.id = :userId")
  boolean existsByJobIdAndUserId(@Param("jobId") String jobId, @Param("userId") String userId);

  @Query("SELECT a FROM UserApplicationJobEntity a "
      + "WHERE a.jobPost.id = :jobId AND a.user.id = :userId")
  Optional<UserApplicationJobEntity> findByJobIdAndUserId(@Param("jobId") String jobId,
      @Param("userId") String userId);

  @Query("SELECT COUNT(a) FROM UserApplicationJobEntity a WHERE a.jobPost.id = :jobId")
  long countByJobId(@Param("jobId") String jobId);

  @Query(value = "SELECT new com.lynq.backend.repository.projection.JobCandidateProjection("
      + "a.id, u.id, j.id, u.fullName, u.profileImageUrl, u.currentPosition, a.appliedOn, "
      + "CAST((SELECT function('group_concat', jsk.skill) FROM JobPostSkillEntity jsk "
      + "WHERE jsk.jobPost = j) AS string), "
      + "CAST((SELECT function('group_concat', usk.skill) FROM UserSkillsEntity usk "
      + "WHERE usk.user = u) AS string)) "
      + "FROM UserApplicationJobEntity a "
      + "JOIN a.user u "
      + "JOIN a.jobPost j "
      + "WHERE j.id = :jobId "
      + "ORDER BY a.appliedOn DESC",
      countQuery = "SELECT COUNT(a) FROM UserApplicationJobEntity a WHERE a.jobPost.id = :jobId")
  Page<JobCandidateProjection> findCandidatesByJobId(@Param("jobId") String jobId, Pageable pageable);
}
