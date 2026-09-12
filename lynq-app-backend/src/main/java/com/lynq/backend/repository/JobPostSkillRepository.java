package com.lynq.backend.repository;

import com.lynq.backend.model.JobPostEntity;
import com.lynq.backend.model.JobPostSkillEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface JobPostSkillRepository extends JpaRepository<JobPostSkillEntity, String> {

  List<JobPostSkillEntity> findByJobPost(JobPostEntity jobPost);
}
