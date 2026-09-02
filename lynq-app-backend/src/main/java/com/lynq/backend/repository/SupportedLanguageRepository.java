package com.lynq.backend.repository;

import com.lynq.backend.model.SupportedLanguageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SupportedLanguageRepository extends JpaRepository<SupportedLanguageEntity, String> {
}
