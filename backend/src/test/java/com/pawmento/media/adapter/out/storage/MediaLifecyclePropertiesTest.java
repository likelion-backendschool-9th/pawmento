package com.pawmento.media.adapter.out.storage;

import static org.assertj.core.api.Assertions.*;

import com.pawmento.media.domain.MediaPolicy;
import java.time.Duration;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class MediaLifecyclePropertiesTest {
  @Test
  @DisplayName("#33-S7 미디어 TTL 설정이 실제 도메인 정책 빈으로 바인딩된다")
  void configuredPolicy() {
    new ApplicationContextRunner()
        .withUserConfiguration(MediaStorageConfig.class)
        .withPropertyValues(
            "pawmento.media.lifecycle.session-ttl=2m",
            "pawmento.media.lifecycle.unattached-ttl=3h",
            "pawmento.media.lifecycle.work-lease=4m")
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              var policy = context.getBean(MediaPolicy.class);
              assertThat(policy.sessionTtl()).isEqualTo(Duration.ofMinutes(2));
              assertThat(policy.unattachedTtl()).isEqualTo(Duration.ofHours(3));
              assertThat(policy.workLease()).isEqualTo(Duration.ofMinutes(4));
            });
  }
}
