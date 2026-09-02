package com.lynq.backend.repository;

import com.lynq.backend.model.UserSimilarityTagEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserSimilarityTagRepository extends JpaRepository<UserSimilarityTagEntity, String> {
}
