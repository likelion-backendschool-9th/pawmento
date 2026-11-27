package com.pawmento.media.application.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.pawmento.media.application.exception.MediaAssetNotFoundException;
import com.pawmento.media.application.port.in.*;
import com.pawmento.media.application.port.out.*;
import com.pawmento.media.domain.MediaPurpose;
import java.time.Clock;
import java.util.List;
import org.junit.jupiter.api.*;

class MediaReferencesOwnershipTest {
  @Test
  @DisplayName("#32-S10 없는/타인/다른 아이 자산이 섞이면 행 잠금을 하나도 얻지 않는다")
  void identityCheckPrecedesAllLocks() {
    var load = mock(LockMediaAssetPort.class);
    var owners = mock(LoadMediaOwnershipPort.class);
    var service =
        new ChangeMediaReferencesService(
            load,
            owners,
            mock(SaveMediaAssetPort.class),
            mock(InspectMediaReferencesPort.class),
            mock(MediaWorkQueuePort.class),
            Clock.systemUTC());
    var allowed = new MediaOwnership(1L, 10L, 20L, MediaPurpose.DIARY_ATTACHMENT);
    for (var found :
        List.of(
            List.of(allowed),
            List.of(allowed, new MediaOwnership(2L, 11L, 20L, MediaPurpose.DIARY_ATTACHMENT)),
            List.of(allowed, new MediaOwnership(2L, 10L, 21L, MediaPurpose.DIARY_ATTACHMENT)),
            List.of(
                allowed,
                new MediaOwnership(2L, 10L, 20L, MediaPurpose.QUESTION_ANSWER_ATTACHMENT)))) {
      when(owners.loadOwnership(List.of(1L, 2L))).thenReturn(found);
      assertThatThrownBy(
              () -> service.lock(new LockMediaReferencesCommand(10L, 20L, List.of(2L, 1L))))
          .isInstanceOf(MediaAssetNotFoundException.class);
      verifyNoInteractions(load);
    }
  }

  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.ValueSource(
      strings = {"DIARY_ATTACHMENT", "QUESTION_ANSWER_ATTACHMENT"})
  @DisplayName("#33-S20 잠금과 후속 검증은 기존 연결의 타인 업로더를 허용하되 신규 연결은 거부한다 (고침 #133: Q2)")
  void previousAndNewReferencesUseSamePolicy(String purpose) {
    var load = mock(LockMediaAssetPort.class);
    var owners = mock(LoadMediaOwnershipPort.class);
    var assets = mock(LoadMediaAssetPort.class);
    var locks =
        new ChangeMediaReferencesService(
            load,
            owners,
            mock(SaveMediaAssetPort.class),
            mock(InspectMediaReferencesPort.class),
            mock(MediaWorkQueuePort.class),
            Clock.systemUTC());
    var validation = new ValidateMediaReferencesService(owners, assets);
    var identity = new MediaOwnership(1L, 11L, 20L, MediaPurpose.valueOf(purpose));
    when(owners.loadOwnership(List.of(1L))).thenReturn(List.of(identity));
    when(load.findForUpdate(1L))
        .thenReturn(java.util.Optional.of(mock(com.pawmento.media.domain.MediaAsset.class)));
    for (var next : List.of(List.<Long>of(), List.of(1L))) {
      locks.lock(new LockMediaReferencesCommand(10L, 20L, List.of(1L), next, purpose));
      validation.validate(new ValidateMediaReferencesCommand(10L, 20L, List.of(1L), next, purpose));
    }
    verifyNoInteractions(assets); // 기존 연결의 유지/해제는 새 업로드의 READY 검사가 아니다.
    clearInvocations(load);
    assertThatThrownBy(
            () ->
                locks.lock(
                    new LockMediaReferencesCommand(10L, 20L, List.of(), List.of(1L), purpose)))
        .isInstanceOf(MediaAssetNotFoundException.class);
    assertThatThrownBy(
            () ->
                validation.validate(
                    new ValidateMediaReferencesCommand(10L, 20L, List.of(), List.of(1L), purpose)))
        .isInstanceOf(MediaAssetNotFoundException.class);
    verifyNoInteractions(load);
    for (var bad :
        List.of(
            new MediaOwnership(1L, 11L, 21L, MediaPurpose.valueOf(purpose)),
            new MediaOwnership(1L, 11L, 20L, MediaPurpose.PET_PROFILE))) {
      when(owners.loadOwnership(List.of(1L))).thenReturn(List.of(bad));
      assertThatThrownBy(
              () ->
                  locks.lock(
                      new LockMediaReferencesCommand(10L, 20L, List.of(1L), List.of(), purpose)))
          .isInstanceOf(MediaAssetNotFoundException.class);
      assertThatThrownBy(
              () ->
                  validation.validate(
                      new ValidateMediaReferencesCommand(
                          10L, 20L, List.of(1L), List.of(), purpose)))
          .isInstanceOf(MediaAssetNotFoundException.class);
    }
  }
}
