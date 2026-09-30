package com.lynq.analytics.repository;

import com.lynq.analytics.model.CandidateEntity;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface CandidateRepository extends JpaRepository<CandidateEntity, String> {

  @EntityGraph(attributePaths = {"tags", "skills"})
  @Query("""
      select distinct c from CandidateEntity c
      where (:includeSynthetic = true or c.synthetic = false)
        and c.id in (select s.id from CandidateEntity s join s.tags t where t in :tags)""")
  List<CandidateEntity> findSharingTags(@Param("tags") Collection<String> tags,
      @Param("includeSynthetic") boolean includeSynthetic);
}
