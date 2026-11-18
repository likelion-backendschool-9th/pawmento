package com.pawmento.media.adapter.out.persistence;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.pawmento.media.application.exception.*;
import com.pawmento.media.domain.MediaAsset;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

class MediaPersistenceExceptionTest {
  private final MediaAssetJpaRepository repository = mock(MediaAssetJpaRepository.class);
  private final MediaPersistenceAdapter adapter =
      new MediaPersistenceAdapter(
          repository,
          mock(EntityManager.class),
          mock(MediaValidationJobJpaRepository.class),
          com.pawmento.media.domain.MediaPolicy.defaults());

  @Test
  @DisplayName("#33-S16 DB 장애는 어댑터에서 애플리케이션 예외로 바꾸고 원인을 보존한다")
  void databaseFailure() {
    var original = new DataAccessResourceFailureException("internal database details");
    when(repository.findById(1L)).thenThrow(original);
    assertThatThrownBy(() -> adapter.findById(1L))
        .isInstanceOf(MediaPersistenceUnavailableException.class)
        .hasCause(original)
        .hasMessageNotContaining("internal database details");
  }

  @Test
  @DisplayName("#33-S4 낙관적 잠금 실패는 상태 충돌 예외로 변환한다")
  void concurrentChange() {
    var original = new ObjectOptimisticLockingFailureException(MediaAssetJpaEntity.class, 1L);
    when(repository.saveAndFlush(any())).thenThrow(original);
    var asset =
        MediaAsset.create(
            1L,
            2L,
            "DIARY_ATTACHMENT",
            "a.png",
            "image/png",
            1,
            UUID.randomUUID().toString(),
            Instant.now());
    assertThatThrownBy(() -> adapter.save(asset))
        .isInstanceOf(MediaConcurrentModificationException.class)
        .hasCause(original);
  }

  @Test
  @DisplayName("#33-S16 프로그래밍 오류를 DB 장애나 사용자 오류로 바꾸지 않는다")
  void programmingFailure() {
    var original = new IllegalArgumentException("unexpected bug");
    when(repository.findById(1L)).thenThrow(original);
    assertThatThrownBy(() -> adapter.findById(1L)).isSameAs(original);
  }
}
