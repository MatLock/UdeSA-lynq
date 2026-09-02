package com.lynq.backend.repository;

import com.lynq.backend.model.CompanyEntity;
import com.lynq.backend.model.UserEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CompanyRepository extends JpaRepository<CompanyEntity, String> {

  boolean existsByName(String name);

  Optional<CompanyEntity> findByOwner(UserEntity owner);

  Optional<CompanyEntity> findByOwnerId(String ownerId);
}