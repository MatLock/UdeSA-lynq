package com.lynq.analytics.service;

import lombok.extern.log4j.Log4j2;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Log4j2
public class TagFrequencyScheduler {

  private final TagFrequencyService tagFrequencyService;

  public TagFrequencyScheduler(TagFrequencyService tagFrequencyService) {
    this.tagFrequencyService = tagFrequencyService;
  }

  @Scheduled(cron = "${lynq.analytics.tag-frequency.cron:0 0 8 * * *}",
      zone = "${lynq.analytics.tag-frequency.zone:Etc/UTC}")
  public void recomputeDaily() {
    tagFrequencyService.recompute();
  }

  @EventListener(ApplicationReadyEvent.class)
  public void recomputeIfEmpty() {
    if (tagFrequencyService.isEmpty()) {
      log.info("message= Tag frequency is empty, recomputing it on startup");
      tagFrequencyService.recompute();
    }
  }
}
