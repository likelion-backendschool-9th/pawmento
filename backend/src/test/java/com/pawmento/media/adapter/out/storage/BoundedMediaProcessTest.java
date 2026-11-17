package com.pawmento.media.adapter.out.storage;

import static org.assertj.core.api.Assertions.*;

import com.pawmento.media.application.port.out.MediaValidationException;
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
        .isInstanceOf(MediaValidationException.class)
        .hasMessage("PARSER_TIMEOUT");
    assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(4));
  }

  @Test
  @DisplayName("#33-S10 파서의 과도한 출력은 메모리에 쌓지 않고 종료한다")
  void outputLimit() {
    assertThatThrownBy(() -> process.run(List.of("/usr/bin/python3", "-c", "print('x'*70000)")))
        .isInstanceOf(MediaValidationException.class);
  }
}
