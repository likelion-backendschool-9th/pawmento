package com.pawmento.postcard.adapter.in.scheduling;

import static org.assertj.core.api.Assertions.assertThat;

import com.pawmento.postcard.application.port.in.CleanupPostcardFilesUseCase;
import com.pawmento.postcard.application.port.in.ProcessPostcardWorkUseCase;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

// 시나리오는 docs/postcard/generate.md에 있다. 외부 AI 호출이 공용 스케줄 스레드를 붙잡지 않는지 본다(PR #216 리뷰)
class PostcardWorkSchedulerTest {
  @Test
  @DisplayName("#103-S4 생성이 오래 걸려도 스케줄 스레드는 바로 돌아오고, 도는 동안 같은 작업을 겹쳐 넣지 않는다")
  void slowGenerationDoesNotHoldSchedulerThread() throws Exception {
    var started = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var calls = new AtomicInteger();
    ProcessPostcardWorkUseCase slowWorker =
        () -> {
          calls.incrementAndGet();
          started.countDown();
          try {
            release.await(5, TimeUnit.SECONDS);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          }
          return false;
        };
    CleanupPostcardFilesUseCase noCleanup = () -> 0;
    var scheduler = new PostcardWorkScheduler(slowWorker, noCleanup);
    try {
      long before = System.nanoTime();
      scheduler.process();
      long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - before);

      assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();
      assertThat(elapsedMs).isLessThan(1000);

      // 앞 차례가 도는 동안 다시 불려도 새로 넣지 않는다
      scheduler.process();
      scheduler.process();
      assertThat(calls.get()).isEqualTo(1);

      // 앞 차례가 끝나면 다음 호출에서 다시 처리한다
      release.countDown();
      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
      while (calls.get() < 2 && System.nanoTime() < deadline) {
        scheduler.process();
        Thread.sleep(10);
      }
      assertThat(calls.get()).isEqualTo(2);
    } finally {
      release.countDown();
      scheduler.stop();
    }
  }
}
