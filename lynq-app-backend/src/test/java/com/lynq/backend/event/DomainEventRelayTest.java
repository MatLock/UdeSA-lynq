package com.lynq.backend.event;

import com.lynq.backend.event.payload.JobPostReopenedPayload;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;

@SpringJUnitConfig(DomainEventRelayTest.TransactionalContext.class)
class DomainEventRelayTest {

  private static final DomainEvent EVENT = new DomainEvent(
      UUID.fromString("5c8f3a3e-0b7e-5d61-9c1a-2f4b8e6d7a10"), "JobPostReopened", "JOB_POST",
      "77777777-7777-7777-7777-777777777777", Instant.parse("2026-09-20T10:00:00Z"),
      new JobPostReopenedPayload("77777777-7777-7777-7777-777777777777",
          LocalDate.parse("2026-09-20")));

  @Autowired
  private DomainEventPublisher publisher;

  @Autowired
  private SnsDomainEventSender sender;

  @Autowired
  private TransactionTemplate transactionTemplate;

  @BeforeEach
  void setUp() {
    reset(sender);
  }

  @Test
  void theEventIsSentOnlyOnceTheTransactionCommits() {
    transactionTemplate.executeWithoutResult(status -> {
      publisher.publish(EVENT);
      verify(sender, never()).send(any());
    });

    verify(sender).send(EVENT);
  }

  @Test
  void aRolledBackTransactionSendsNothing() {
    transactionTemplate.executeWithoutResult(status -> {
      publisher.publish(EVENT);
      status.setRollbackOnly();
    });

    verify(sender, never()).send(any());
  }

  @Test
  void aTransactionThatFailsSendsNothing() {
    assertThrows(IllegalStateException.class, () ->
        transactionTemplate.executeWithoutResult(status -> {
          publisher.publish(EVENT);
          throw new IllegalStateException("the domain change failed");
        }));

    verify(sender, never()).send(any());
  }

  @Test
  void aSenderThatFailsAfterTheCommitDoesNotFailTheCaller() {
    doThrow(new IllegalStateException("unexpected")).when(sender).send(EVENT);

    transactionTemplate.executeWithoutResult(status -> publisher.publish(EVENT));

    verify(sender).send(EVENT);
  }

  @Test
  void anEventPublishedOutsideATransactionIsNotSent() {
    publisher.publish(EVENT);

    verify(sender, never()).send(any());
  }

  @Configuration
  @EnableTransactionManagement
  @Import({DomainEventPublisher.class, DomainEventRelay.class})
  static class TransactionalContext {

    @Bean
    DataSource dataSource() {
      return new EmbeddedDatabaseBuilder().setType(EmbeddedDatabaseType.H2).build();
    }

    @Bean
    PlatformTransactionManager transactionManager(DataSource dataSource) {
      return new DataSourceTransactionManager(dataSource);
    }

    @Bean
    TransactionTemplate transactionTemplate(PlatformTransactionManager transactionManager) {
      return new TransactionTemplate(transactionManager);
    }

    @Bean
    SnsDomainEventSender sender() {
      return mock(SnsDomainEventSender.class);
    }
  }
}
