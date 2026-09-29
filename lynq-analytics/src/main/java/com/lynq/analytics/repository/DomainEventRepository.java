package com.lynq.analytics.repository;

import com.lynq.analytics.model.DomainEventEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DomainEventRepository extends JpaRepository<DomainEventEntity, Long> {

  boolean existsByEventId(String eventId);

  Optional<DomainEventEntity> findByEventId(String eventId);
}
