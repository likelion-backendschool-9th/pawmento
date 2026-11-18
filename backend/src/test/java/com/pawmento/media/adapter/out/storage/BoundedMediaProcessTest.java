package com.pawmento.media.adapter.out.storage;

import static org.assertj.core.api.Assertions.*;

import com.pawmento.media.application.exception.MediaValidationFailedException;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.*;

class BoundedMediaProcessTest {
  final BoundedMediaProcess process =
      new BoundedMediaProcess(new MediaParserProperties("ffmpeg", "ffprobe", 1));

  @Test
  @DisplayName("#33-S10 파서가 멈추면 제한 시간 안에 프로세스를 종료한다")
  void timeout() {
    long start = System.nanoTime();
    assertThatThrownBy(
            () -> process.run(List.of("/usr/bin/python3", "-c", "import time; time.sleep(10)")))
        .isInstanceOf(
            com.pawmento.media.application.exception.MediaStorageUnavailableException.class)
        .hasCauseInstanceOf(java.util.concurrent.TimeoutException.class);
    assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(4));
  }

  @Test
  @DisplayName("#33-S10 파서의 과도한 출력은 메모리에 쌓지 않고 종료한다")
  void outputLimit() {
    assertThatThrownBy(() -> process.run(List.of("/usr/bin/python3", "-c", "print('x'*70000)")))
        .isInstanceOf(MediaValidationFailedException.class);
  }

  @Test
  @DisplayName("#33-S16 FFmpeg 디스크 쓰기 장애와 알 수 없는 종료는 원인을 보존한 재시도 장애다")
  void outputIoFailure() {
    for (String error :
        new String[] {
          "Error writing trailer: No space left on device",
          "Permission denied",
          "unknown decoder infrastructure fault"
        }) {
      assertThatThrownBy(
              () ->
                  process.run(
                      List.of(
                          "/usr/bin/python3",
                          "-c",
                          "import sys; sys.stderr.write(sys.argv[1]); sys.exit(1)",
                          error)))
          .isInstanceOf(
              com.pawmento.media.application.exception.MediaStorageUnavailableException.class)
          .hasCauseInstanceOf(java.io.IOException.class)
          .satisfies(e -> assertThat(e.getCause().getMessage()).contains(error));
    }
  }

  @Test
  @DisplayName("#33-S3 식별된 형식 불량만 검증 실패로 분류한다")
  void invalidInput() {
    assertThatThrownBy(
            () ->
                process.run(
                    List.of(
                        "/usr/bin/python3",
                        "-c",
                        "import sys;sys.stderr.write('Invalid data found when processing"
                            + " input');sys.exit(1)")))
        .isInstanceOf(MediaValidationFailedException.class)
        .hasCauseInstanceOf(java.io.IOException.class);
  }

  @Test
  @DisplayName("#33-S10 파서 stderr는 배출을 계속하면서 보관 크기를 제한한다")
  void boundedStderr() {
    assertThatThrownBy(
            () ->
                process.run(
                    List.of(
                        "/usr/bin/python3",
                        "-c",
                        "import sys;sys.stderr.write('x'*100000);sys.exit(1)")))
        .isInstanceOf(
            com.pawmento.media.application.exception.MediaStorageUnavailableException.class)
        .satisfies(e -> assertThat(e.getCause().getMessage().length()).isLessThan(17000));
  }
}
