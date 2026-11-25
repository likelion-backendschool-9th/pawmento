package com.pawmento.postcard.adapter.in.scheduling;

import com.pawmento.postcard.application.port.in.*;
import jakarta.annotation.PreDestroy;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.*;

// 서버 한 대 기준으로 엽서를 하나씩 처리한다. 선점이 조건부 갱신이라 서버가 늘어도 중복 호출하지 않는다.
// @Scheduled 작업은 스레드 하나를 같이 쓴다. 외부 AI 호출(장당 최대 60초)이 미디어 처리 같은 다른 작업을
// 막지 않게, 생성은 엽서 전용 스레드에 넘기고 스케줄 스레드는 바로 돌려준다(PR #216 리뷰 반영).
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@Slf4j
@ConditionalOnProperty(
    name = "pawmento.postcard.worker.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class PostcardWorkScheduler {
  private final ProcessPostcardWorkUseCase worker;
  private final CleanupPostcardFilesUseCase cleanup;
  private final ExecutorService generation =
      Executors.newSingleThreadExecutor(Thread.ofPlatform().name("postcard-work").daemon().factory());
  // 앞 차례가 아직 돌고 있으면 새로 넣지 않아 대기열이 쌓이지 않게 한다
  private final AtomicBoolean running = new AtomicBoolean();

  public PostcardWorkScheduler(ProcessPostcardWorkUseCase worker, CleanupPostcardFilesUseCase cleanup) {
    this.worker = worker;
    this.cleanup = cleanup;
  }

  @Scheduled(fixedDelayString = "${pawmento.postcard.worker.delay-ms:3000}")
  public void process() {
    if (!running.compareAndSet(false, true)) return;
    try {
      generation.execute(this::processBatch);
    } catch (RejectedExecutionException e) {
      // 종료 중이다
      running.set(false);
    }
  }

  private void processBatch() {
    try {
      for (int n = 0; n < 5 && worker.processNext(); n++) {}
    } catch (RuntimeException e) {
      log.error("Postcard worker temporarily unavailable: {}", e.getClass().getSimpleName());
    } finally {
      running.set(false);
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

  // 진행 중인 호출은 끊지 않는다. 끝나지 못한 엽서는 선점 10분 뒤 만료 규칙이 실패로 돌린다
  @PreDestroy
  void stop() {
    generation.shutdown();
  }
}
