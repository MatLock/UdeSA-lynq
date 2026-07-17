package com.lynq.backend.repository;

import com.lynq.backend.model.UserResumeEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface UserResumeRepository extends JpaRepository<UserResumeEntity, String> {

  @Query("SELECT r FROM UserResumeEntity r WHERE r.user.id = :userId ORDER BY r.createdOn DESC")
  List<UserResumeEntity> findByUserId(@Param("userId") String userId);
}
