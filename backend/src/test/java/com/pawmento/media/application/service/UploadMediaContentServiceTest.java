package com.pawmento.media.application.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.pawmento.media.application.port.in.*;
import com.pawmento.media.application.port.out.*;
import com.pawmento.media.domain.*;
import java.io.*;
import java.time.Instant;
import org.junit.jupiter.api.*;

class UploadMediaContentServiceTest {
  MediaUploadTransactions tx = mock(MediaUploadTransactions.class);
  StoreMediaUploadPort store = mock(StoreMediaUploadPort.class);
  DeleteMediaUploadPort delete = mock(DeleteMediaUploadPort.class);
  UploadMediaContentService service = new UploadMediaContentService(tx, store, delete);
  MediaAsset asset =
      MediaAsset.create(
          1L,
          2L,
          "DIARY_ATTACHMENT",
          "a.png",
          "image/png",
          3,
          "12345678-1234-1234-1234-123456789abc",
          Instant.now());
  UploadMediaContentCommand command =
      new UploadMediaContentCommand(1L, 2L, 3L, new ByteArrayInputStream(new byte[] {1, 2, 3}));

  @BeforeEach
  void setup() {
    when(tx.claim(1L, 2L, 3L)).thenReturn(asset);
  }

  @Test
  @DisplayName("#33-S16 파일 I/O 실패는 실패 상태와 정리를 수행하고 503으로 안내한다")
  void failedStorage() {
    when(store.store(anyString(), any(), anyLong()))
        .thenThrow(new MediaStorageException(false, new IOException()));
    when(tx.reject(3L)).thenReturn(true);
    assertThatThrownBy(() -> service.upload(command))
        .isInstanceOfSatisfying(
            MediaUploadException.class,
            e -> assertThat(e.kind()).isEqualTo(MediaUploadException.Kind.STORAGE));
    verify(delete).delete(asset.getTemporaryStorageKey());
    verify(tx).cleaned(3L);
    verify(tx, never()).complete(any(), any(), any(), any());
  }

  @Test
  @DisplayName("#33-S5 완료 커밋 결과가 불명확하면 처리 중일 수 있는 파일을 삭제하지 않는다")
  void uncertainCommit() {
    var receipt = new StoredUpload(3, "hash");
    when(store.store(anyString(), any(), anyLong())).thenReturn(receipt);
    when(tx.complete(1L, 2L, 3L, receipt))
        .thenThrow(new IllegalStateException("commit result unknown"));
    when(tx.reject(3L)).thenReturn(false);
    assertThatThrownBy(() -> service.upload(command)).isInstanceOf(IllegalStateException.class);
    verifyNoInteractions(delete);
  }
}
