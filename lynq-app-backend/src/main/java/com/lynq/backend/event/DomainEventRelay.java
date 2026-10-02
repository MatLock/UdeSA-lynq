package com.lynq.backend.event;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class DomainEventRelay {

  private final SnsDomainEventSender sender;

  public DomainEventRelay(SnsDomainEventSender sender) {
    this.sender = sender;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onCommitted(DomainEvent event) {
    sender.send(event);
  }
}
