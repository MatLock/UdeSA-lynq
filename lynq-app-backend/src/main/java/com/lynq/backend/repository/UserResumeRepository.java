package com.lynq.backend.repository;

import com.lynq.backend.model.UserResumeEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface UserResumeRepository extends JpaRepository<UserResumeEntity, String> {

  @Query("SELECT r FROM UserResumeEntity r WHERE r.user.id = :userId ORDER BY r.createdOn DESC")
  List<UserResumeEntity> findByUserId(@Param("userId") String userId);

  /**
   * One of the user's own resumes. Scoped by owner rather than looked up by id
   * and checked afterwards, so a resume belonging to somebody else is simply
   * absent — never acknowledged as existing.
   */
  @Query("SELECT r FROM UserResumeEntity r WHERE r.id = :resumeId AND r.user.id = :userId")
  Optional<UserResumeEntity> findByIdAndUserId(@Param("resumeId") String resumeId,
      @Param("userId") String userId);
}
