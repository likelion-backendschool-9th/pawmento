package com.pawmento.media.application.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.pawmento.media.application.port.out.*;
import com.pawmento.media.domain.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.*;

class ProcessMediaWorkServiceTest {
  final MediaWorkTransactions tx = mock(MediaWorkTransactions.class);
  final LoadMediaAssetPort load = mock(LoadMediaAssetPort.class);
  final ValidateMediaFilePort validator = mock(ValidateMediaFilePort.class);
  final DeleteMediaFilesPort deletion = mock(DeleteMediaFilesPort.class);
  final DeleteMediaUploadPort temporary = mock(DeleteMediaUploadPort.class);
  final ProcessMediaWorkService service =
      new ProcessMediaWorkService(tx, load, validator, deletion, temporary);
  final MediaWork work = new MediaWork(1L, UUID.randomUUID().toString(), 1, "VALIDATE");

  MediaAsset asset() {
    var a =
        MediaAsset.create(
            1L,
            2L,
            "DIARY_ATTACHMENT",
            "a.png",
            "image/png",
            3,
            UUID.randomUUID().toString(),
            Instant.now());
    a.begin(Instant.now());
    a.received(new StoredUpload(3, "hash"), Instant.now());
    return a;
  }

  @BeforeEach
  void setup() {
    when(tx.claim()).thenReturn(Optional.of(work));
    when(load.findById(1L)).thenReturn(Optional.of(asset()));
  }

  @Test
  @DisplayName("#33-S5 최종 파일 생성 뒤 DB 실패 시 파일을 삭제하지 않고 재시도한다")
  void uncertainCommit() {
    var result = new VerifiedMedia(work.token(), "image/png", 3, "a".repeat(64), null, 1, 1, 2L);
    when(validator.validate(any(), eq(work.token()))).thenReturn(result);
    doThrow(new IllegalStateException("database")).when(tx).success(work, result);
    assertThat(service.processNext()).isTrue();
    verify(tx).retry(work, "IllegalStateException");
    verifyNoInteractions(deletion, temporary);
  }

  @Test
  @DisplayName("#33-S16 READY 이후 임시 파일 정리 실패는 READY를 유지하고 재시도한다")
  void cleanupFailure() {
    var a = asset();
    a.validated(
        new VerifiedMedia(work.token(), "image/png", 3, "a".repeat(64), null, 1, 1, null),
        Instant.now());
    when(load.findById(1L)).thenReturn(Optional.of(a));
    doThrow(new MediaStorageException(false, null))
        .when(temporary)
        .delete(a.getTemporaryStorageKey());
    service.processNext();
    verifyNoInteractions(validator);
    verify(tx).retry(work, "MediaStorageException");
    verify(tx, never()).finished(work);
  }

  @Test
  @DisplayName("#33-S9 물리 삭제 실패는 DB 완료로 표시하지 않고 삭제 작업을 재시도한다")
  void deletionFailure() {
    var a = asset();
    a.requestDeletion(Instant.now());
    var w = new MediaWork(1L, work.token(), 1, "DELETE");
    when(tx.claim()).thenReturn(Optional.of(w));
    when(load.findById(1L)).thenReturn(Optional.of(a));
    doThrow(new MediaStorageException(false, null))
        .when(deletion)
        .deleteAll(a.getTemporaryStorageKey(), null);
    service.processNext();
    verify(tx).retry(w, "MediaStorageException");
    verify(tx, never()).deleted(w);
  }

  @Test
  @DisplayName("#33-S7 이전 임대 완료가 거부되면 새 작업이 사용할 원본을 삭제하지 않는다")
  void staleWorkerKeepsInput() {
    var result = new VerifiedMedia(work.token(), "image/png", 3, "a".repeat(64), null, 1, 1, null);
    when(validator.validate(any(), eq(work.token()))).thenReturn(result);
    when(tx.success(work, result)).thenReturn(false);
    service.processNext();
    verifyNoInteractions(temporary, deletion);
    verify(tx, never()).finished(work);
  }
}
