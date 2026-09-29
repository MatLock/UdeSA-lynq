package com.lynq.analytics.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "domain_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DomainEventEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id", nullable = false)
  private Long id;

  @Column(name = "event_id", length = 36, nullable = false, unique = true)
  private String eventId;

  @Column(name = "event_type", length = 64, nullable = false)
  private String eventType;

  @Column(name = "aggregate_type", length = 32, nullable = false)
  private String aggregateType;

  @Column(name = "aggregate_id", length = 36, nullable = false)
  private String aggregateId;

  @Column(name = "payload", columnDefinition = "JSON", nullable = false)
  private String payload;

  @Column(name = "occurred_on", nullable = false)
  private Instant occurredOn;

  @Column(name = "received_on", nullable = false)
  private Instant receivedOn;

}
