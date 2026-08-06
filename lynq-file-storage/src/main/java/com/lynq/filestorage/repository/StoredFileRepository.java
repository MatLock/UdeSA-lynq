package com.lynq.filestorage.repository;

import com.lynq.filestorage.model.StoredFileEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StoredFileRepository extends JpaRepository<StoredFileEntity, String> {
}
