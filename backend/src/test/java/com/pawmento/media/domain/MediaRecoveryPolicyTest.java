package com.pawmento.media.domain;

import static org.assertj.core.api.Assertions.*;

import com.pawmento.media.domain.exception.*;
import java.time.*;
import java.util.UUID;
import org.junit.jupiter.api.*;

class MediaRecoveryPolicyTest {
  static final Instant NOW = Instant.parse("2026-11-17T00:00:00Z");
  static final String KEY = "12345678-1234-1234-1234-123456789abc";

  MediaAsset restore(
      MediaUploadStatus status,
      Long member,
      int refs,
      VerifiedMedia verified,
      Long size,
      String hash,
      Instant ready,
      Instant expiry) {
    return MediaAsset.restore(
        1L,
        member,
        2L,
        MediaPurpose.DIARY_ATTACHMENT,
        MediaType.IMAGE,
        "a.png",
        "image/png",
        3,
        KEY,
        status,
        NOW,
        NOW.plusSeconds(600),
        NOW,
        size,
        hash,
        null,
        false,
        0L,
        verified,
        ready,
        expiry,
        null,
        refs);
  }

  @Test
  @DisplayName("#33-S5 복원은 필수 값·음수 참조·READY/검증 결과·수신 메타데이터 불변 조건을 검사한다")
  void restoredInvariants() {
    assertThat(restore(MediaUploadStatus.PENDING, 1L, 0, null, null, null, null, null)).isNotNull();
    assertThatThrownBy(
            () -> restore(MediaUploadStatus.PENDING, null, 0, null, null, null, null, null))
        .isInstanceOf(InvalidMediaStateException.class);
    assertThatThrownBy(
            () -> restore(MediaUploadStatus.PENDING, 1L, -1, null, null, null, null, null))
        .isInstanceOf(InvalidMediaStateException.class);
    assertThatThrownBy(
            () ->
                restore(
                    MediaUploadStatus.READY,
                    1L,
                    0,
                    null,
                    3L,
                    "a".repeat(64),
                    NOW,
                    NOW.plusSeconds(600)))
        .isInstanceOf(InvalidMediaStateException.class);
    assertThatThrownBy(
            () -> restore(MediaUploadStatus.PROCESSING, 1L, 0, null, null, null, null, null))
        .isInstanceOf(InvalidMediaStateException.class);
    var verified = new VerifiedMedia(KEY, "image/png", 3, "b".repeat(64), null, 1, 1, 1L);
    assertThat(
            restore(
                MediaUploadStatus.READY,
                1L,
                0,
                verified,
                3L,
                "a".repeat(64),
                NOW,
                NOW.plusSeconds(600)))
        .isNotNull();
  }

  @Test
  @DisplayName("#33-S4 작업 도메인이 임대 교체와 이전 토큰 거부를 결정한다")
  void lease() {
    var policy = MediaPolicy.defaults();
    var job = MediaJob.pending(1L, NOW);
    var first = job.claim(NOW, UUID.randomUUID().toString(), policy);
    assertThatThrownBy(() -> job.claim(NOW, UUID.randomUUID().toString(), policy))
        .isInstanceOf(InvalidMediaStateException.class);
    var next = job.claim(NOW.plus(policy.workLease()), UUID.randomUUID().toString(), policy);
    assertThat(job.owns(first)).isFalse();
    assertThat(job.owns(next)).isTrue();
    assertThat(job.getAttempts()).isEqualTo(2);
  }

  @Test
  @DisplayName("#33-S16 저장소 장애는 다섯 번이 지나도 원본을 거부하지 않고 재시도한다")
  void retryAndPermanentFailure() {
    var policy = MediaPolicy.defaults();
    var job = MediaJob.pending(1L, NOW);
    var now = NOW;
    for (int n = 0; n < 6; n++) {
      job.claim(now, UUID.randomUUID().toString(), policy);
      job.fail(now, "MediaStorageUnavailableException", true, policy);
      assertThat(job.getStatus()).isEqualTo(MediaJob.Status.RETRY);
      now = job.getNextAttemptAt();
    }
    assertThat(job.getAttempts()).isEqualTo(6);
    job.claim(now, UUID.randomUUID().toString(), policy);
    job.fail(now, "IllegalArgumentException", false, policy);
    assertThat(job.getStatus()).isEqualTo(MediaJob.Status.FAILED);
    assertThatThrownBy(
            () -> job.claim(NOW.plusSeconds(100000), UUID.randomUUID().toString(), policy))
        .isInstanceOf(InvalidMediaStateException.class);
  }

  @Test
  @DisplayName("#33-S7 설정된 세션·미연결 수명과 업로드 한도는 도메인 정책으로 적용한다")
  void configurableLifetimes() {
    var policy =
        new MediaPolicy(
            Duration.ofMinutes(2),
            Duration.ofHours(2),
            Duration.ofMinutes(3),
            Duration.ofMinutes(1),
            2);
    var a =
        MediaAsset.create(1L, 2L, "DIARY_ATTACHMENT", "a.png", "image/png", 3, KEY, NOW, policy);
    assertThat(a.getExpiresAt()).isEqualTo(NOW.plusSeconds(120));
    a.begin(NOW);
    a.received(new StoredUpload(3, "a".repeat(64)), NOW);
    a.validated(
        new VerifiedMedia(KEY, "image/png", 3, "b".repeat(64), null, 1, 1, 1L), NOW, policy);
    assertThat(a.getUnattachedExpiresAt()).isEqualTo(NOW.plusSeconds(7200));
    assertThatThrownBy(() -> policy.requireUploadSlot(2))
        .isInstanceOf(MediaUploadLimitException.class);
  }
}
