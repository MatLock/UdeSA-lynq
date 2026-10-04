package com.lynq.analytics.cache;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@ExtendWith(MockitoExtension.class)
class CompanyJobsCacheEvictorTest {

  private static final String USER_ID = "11111111-1111-1111-1111-111111111111";

  @Mock
  private CacheManager cacheManager;

  @Mock
  private Cache cache;

  private CompanyJobsCacheEvictor companyJobsCacheEvictor;

  @BeforeEach
  void setUp() {
    companyJobsCacheEvictor = new CompanyJobsCacheEvictor(cacheManager);
  }

  @AfterEach
  void tearDown() {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.clearSynchronization();
    }
  }

  @Test
  void evictsRightAwayOutsideATransaction() {
    when(cacheManager.getCache(AnalyticsCaches.COMPANY_JOBS)).thenReturn(cache);

    companyJobsCacheEvictor.evictAfterCommit(USER_ID);

    verify(cache).evict(USER_ID);
  }

  @Test
  void waitsForTheCommitInsideATransaction() {
    TransactionSynchronizationManager.initSynchronization();

    companyJobsCacheEvictor.evictAfterCommit(USER_ID);

    verifyNoInteractions(cacheManager);
    when(cacheManager.getCache(AnalyticsCaches.COMPANY_JOBS)).thenReturn(cache);
    TransactionSynchronizationManager.getSynchronizations()
        .forEach(TransactionSynchronization::afterCommit);
    verify(cache).evict(USER_ID);
  }

  @Test
  void ignoresAJobPostWithoutCreator() {
    companyJobsCacheEvictor.evictAfterCommit(null);

    verifyNoInteractions(cacheManager);
  }

  @Test
  void survivesACacheFailure() {
    when(cacheManager.getCache(AnalyticsCaches.COMPANY_JOBS)).thenReturn(cache);
    doThrow(new IllegalStateException("redis down")).when(cache).evict(USER_ID);

    companyJobsCacheEvictor.evictAfterCommit(USER_ID);

    verify(cache).evict(USER_ID);
  }
}
