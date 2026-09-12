package com.lynq.backend.repository;

import com.lynq.backend.model.JobPostEntity;
import com.lynq.backend.model.JobPostSimilarityTagEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface JobPostSimilarityTagRepository extends JpaRepository<JobPostSimilarityTagEntity, String> {

  List<JobPostSimilarityTagEntity> findByJobPost(JobPostEntity jobPost);
}
