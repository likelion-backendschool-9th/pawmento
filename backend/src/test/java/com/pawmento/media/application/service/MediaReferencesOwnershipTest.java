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
}
