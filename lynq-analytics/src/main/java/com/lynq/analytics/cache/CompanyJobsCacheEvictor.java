package com.lynq.analytics.cache;

import lombok.extern.log4j.Log4j2;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
@Log4j2
public class CompanyJobsCacheEvictor {

  private final CacheManager cacheManager;

  public CompanyJobsCacheEvictor(CacheManager cacheManager) {
    this.cacheManager = cacheManager;
  }

  public void evictAfterCommit(String userId) {
    if (userId == null) {
      return;
    }
    if (!TransactionSynchronizationManager.isSynchronizationActive()) {
      evict(userId);
      return;
    }
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
      @Override
      public void afterCommit() {
        evict(userId);
      }
    });
  }

  private void evict(String userId) {
    try {
      Cache cache = cacheManager.getCache(AnalyticsCaches.COMPANY_JOBS);
      if (cache != null) {
        cache.evict(userId);
      }
    } catch (RuntimeException e) {
      log.warn("message= Could not evict the company job posts of user '{}'", userId, e);
    }
  }
}
