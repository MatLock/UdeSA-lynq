package com.lynq.analytics.repository;

import com.lynq.analytics.enums.JobStatus;
import com.lynq.analytics.model.JobPostEntity;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface JobPostRepository extends JpaRepository<JobPostEntity, String> {

  @EntityGraph(attributePaths = {"tags", "skills"})
  @Query("""
      select distinct j from JobPostEntity j
      where j.id <> :jobId
        and j.id in (select s.id from JobPostEntity s join s.tags t where t in :tags)""")
  List<JobPostEntity> findSharingTags(@Param("tags") Collection<String> tags,
      @Param("jobId") String jobId);

  @EntityGraph(attributePaths = {"tags", "skills"})
  @Query("select distinct j from JobPostEntity j where j.status = :status")
  List<JobPostEntity> findWithProfileByStatus(@Param("status") JobStatus status);

  List<JobPostEntity> findByCreatedByUserIdOrderByPublishedOnDescIdAsc(String createdByUserId);

  List<JobPostEntity> findByStatusAndClosedOnIsNotNull(JobStatus status);

  @Query("""
      select j.publishedOn as publishedOn, count(j) as jobPosts
      from JobPostEntity j
      where j.publishedOn >= :from and j.publishedOn <= :to
      group by j.publishedOn""")
  List<PublishedCount> countPublishedBetween(@Param("from") LocalDate from,
      @Param("to") LocalDate to);

  interface PublishedCount {

    LocalDate getPublishedOn();

    long getJobPosts();
  }
}
