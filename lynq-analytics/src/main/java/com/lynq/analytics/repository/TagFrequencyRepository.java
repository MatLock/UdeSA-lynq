package com.lynq.analytics.repository;

import com.lynq.analytics.model.TagFrequencyEntity;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface TagFrequencyRepository extends JpaRepository<TagFrequencyEntity, String> {

  @Query(value = """
      SELECT LOWER(t.tag) AS tag, COUNT(*) AS df
      FROM job_post_tags t
      GROUP BY LOWER(t.tag)""", nativeQuery = true)
  List<TagCount> countJobPostTags();

  @Modifying
  @Query("delete from TagFrequencyEntity f where f.computedOn < :computedOn")
  int deleteComputedBefore(@Param("computedOn") Instant computedOn);

  interface TagCount {

    String getTag();

    long getDf();
  }
}
