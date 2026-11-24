package com.pawmento.postcard.adapter.in.scheduling;

import com.pawmento.postcard.application.port.in.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.*;

// 서버 한 대 기준으로 엽서를 하나씩 처리한다. 선점이 조건부 갱신이라 서버가 늘어도 중복 호출하지 않는다.
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(
    name = "pawmento.postcard.worker.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class PostcardWorkScheduler {
  private final ProcessPostcardWorkUseCase worker;
  private final CleanupPostcardFilesUseCase cleanup;

  @Scheduled(fixedDelayString = "${pawmento.postcard.worker.delay-ms:3000}")
  public void process() {
    try {
      for (int n = 0; n < 5 && worker.processNext(); n++) {}
    } catch (RuntimeException e) {
      log.error("Postcard worker temporarily unavailable: {}", e.getClass().getSimpleName());
    }
  }

  @Scheduled(fixedDelayString = "${pawmento.postcard.worker.cleanup-delay-ms:3600000}")
  public void clean() {
    try {
      cleanup.cleanup();
    } catch (RuntimeException e) {
      log.error("Postcard file cleanup temporarily unavailable: {}", e.getClass().getSimpleName());
    }
  }
}
