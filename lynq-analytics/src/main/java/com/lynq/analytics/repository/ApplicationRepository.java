package com.lynq.analytics.repository;

import com.lynq.analytics.model.ApplicationEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ApplicationRepository extends JpaRepository<ApplicationEntity, String> {

  Optional<ApplicationEntity> findByJobIdAndCandidateId(String jobId, String candidateId);

  @Query("select a.lynqScore from ApplicationEntity a where a.jobId = :jobId")
  List<Integer> findScoresByJobId(@Param("jobId") String jobId);

  @Query("""
      select a.jobId as jobId, a.lynqScore as lynqScore
      from ApplicationEntity a
      where a.jobId in :jobIds""")
  List<JobScore> findScoresByJobIds(@Param("jobIds") Collection<String> jobIds);

  interface JobScore {

    String getJobId();

    int getLynqScore();
  }
}
