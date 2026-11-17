package com.pawmento.media.adapter.in.scheduling;

import com.pawmento.media.application.port.in.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.*;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(
    name = "pawmento.media.worker.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class MediaWorkScheduler {
  private final ProcessMediaWorkUseCase worker;
  private final CleanupMediaUseCase cleanup;

  @Scheduled(fixedDelayString = "${pawmento.media.worker.delay-ms:2000}")
  public void process() {
    try {
      for (int n = 0; n < 10 && worker.processNext(); n++) {}
    } catch (RuntimeException e) {
      log.warn("Media worker temporarily unavailable: {}", e.getClass().getSimpleName());
    }
  }

  @Scheduled(fixedDelayString = "${pawmento.media.worker.cleanup-delay-ms:60000}")
  public void clean() {
    try {
      cleanup.cleanup();
    } catch (RuntimeException e) {
      log.warn("Media cleanup temporarily unavailable: {}", e.getClass().getSimpleName());
    }
  }
}
