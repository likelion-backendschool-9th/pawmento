package com.pawmento.media.domain;

import static org.assertj.core.api.Assertions.*;

import com.pawmento.media.domain.exception.*;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MediaAssetTest {
  private static final Instant NOW = Instant.parse("2026-11-17T00:00:00Z");

  private MediaAsset asset(long size) {
    return MediaAsset.create(
        1L,
        2L,
        "DIARY_ATTACHMENT",
        "보리.png",
        "image/png",
        size,
        "12345678-1234-1234-1234-123456789abc",
        NOW);
  }

  @Test
  @DisplayName("#33-S1 수신 완료는 검증 대기 PROCESSING이며 READY가 아니다")
  void receive() {
    var a = asset(3);
    a.begin(NOW);
    a.received(new StoredUpload(3, "digest"), NOW.plusSeconds(1));
    assertThat(a.getStatus()).isEqualTo(MediaUploadStatus.PROCESSING);
    assertThat(a.getReceivedSizeBytes()).isEqualTo(3);
  }

  @Test
  @DisplayName("#33-S15 파일당 15MiB 경계는 허용하고 1바이트 초과·빈 파일은 거부한다")
  void limits() {
    assertThat(asset(MediaAsset.MAX_FILE_BYTES).getDeclaredSizeBytes())
        .isEqualTo(MediaAsset.MAX_FILE_BYTES);
    assertThatThrownBy(() -> asset(MediaAsset.MAX_FILE_BYTES + 1))
        .isInstanceOf(MediaSizeLimitExceededException.class);
    assertThatThrownBy(() -> asset(0)).isInstanceOf(InvalidMediaException.class);
  }

  @Test
  @DisplayName("#33-S3 사진·녹음 신고 형식만 허용하고 영상·HTML은 거부한다")
  void formats() {
    for (String mime : new String[] {"video/mp4", "text/html", "image/svg+xml"})
      assertThatThrownBy(
              () ->
                  MediaAsset.create(
                      1L,
                      2L,
                      "DIARY_ATTACHMENT",
                      "x",
                      mime,
                      1,
                      "12345678-1234-1234-1234-123456789abc",
                      NOW))
          .isInstanceOf(UnsupportedMediaFormatException.class);
    assertThat(
            MediaAsset.create(
                    1L,
                    2L,
                    "QUESTION_ANSWER_ATTACHMENT",
                    "녹음.webm",
                    "audio/webm;codecs=opus",
                    1,
                    "12345678-1234-1234-1234-123456789abc",
                    NOW)
                .getMediaType())
        .isEqualTo(MediaType.AUDIO);
  }

  @Test
  @DisplayName("#33-S4 이미 수신 중인 세션과 만료 시각부터의 수신은 거부한다")
  void state() {
    var a = asset(3);
    a.begin(NOW);
    assertThatThrownBy(() -> a.begin(NOW)).isInstanceOf(MediaStateConflictException.class);
    assertThatThrownBy(
            () ->
                asset(3)
                    .begin(NOW.plus(com.pawmento.media.domain.MediaPolicy.defaults().sessionTtl())))
        .isInstanceOf(MediaStateConflictException.class);
  }

  @Test
  @DisplayName("#33-S2 업로더와 반려동물이 다르면 접근할 수 없다")
  void ownership() {
    var a = asset(3);
    assertThatThrownBy(() -> a.requireOwner(3L, 2L)).isInstanceOf(MediaAccessDeniedException.class);
    assertThatThrownBy(() -> a.requireOwner(1L, 3L)).isInstanceOf(MediaAccessDeniedException.class);
  }

  @Test
  @DisplayName("#33-S16 크기 불일치·수신 실패는 완료 상태로 넘어가지 않는다")
  void failure() {
    var a = asset(3);
    a.begin(NOW);
    assertThatThrownBy(() -> a.received(new StoredUpload(2, "digest"), NOW))
        .isInstanceOf(InvalidMediaException.class);
    assertThat(a.rejectUpload(NOW)).isTrue();
    a.temporaryRemoved(NOW);
    assertThat(a.getStatus()).isEqualTo(MediaUploadStatus.REJECTED);
    assertThat(a.isTemporaryFileDeleted()).isTrue();
  }
}
