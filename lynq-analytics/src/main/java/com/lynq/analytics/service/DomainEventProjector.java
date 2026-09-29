package com.lynq.analytics.service;

import com.lynq.analytics.listener.message.DomainEventMessage;

public interface DomainEventProjector {

  boolean supports(String eventType);

  void project(DomainEventMessage message);
}
