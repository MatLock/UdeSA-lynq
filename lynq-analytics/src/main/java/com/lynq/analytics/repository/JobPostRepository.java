package com.lynq.analytics.repository;

import com.lynq.analytics.model.JobPostEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface JobPostRepository extends JpaRepository<JobPostEntity, String> {
}
