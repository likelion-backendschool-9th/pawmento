package com.pawmento.diary.application.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.pawmento.diary.application.exception.DiaryFeatureUnavailableException;
import com.pawmento.diary.application.port.in.*;
import com.pawmento.diary.application.port.out.*;
import java.time.*;
import org.junit.jupiter.api.*;

class DiaryWritePolicyTest {
  private final CheckDiaryPetAccessPort access = mock(CheckDiaryPetAccessPort.class);
  private final SaveDiaryEntryPort save = mock(SaveDiaryEntryPort.class);
  private final LoadDiaryEntryPort load = mock(LoadDiaryEntryPort.class);
  private final Clock clock = Clock.fixed(Instant.parse("2026-11-17T15:00:00Z"), ZoneOffset.UTC);
  private final DiaryWriteFeatures[] unsupported = {
    new DiaryWriteFeatures(null, true), new DiaryWriteFeatures(java.util.List.of(1L), true)
  };

  @Test
  @DisplayName("#32-S23 웹을 거치지 않은 생성도 미지원 위치를 거부하고 저장하지 않는다")
  void createPolicy() {
    var service =
        new CreateDiaryEntryService(
            access,
            mock(DiaryAttachmentsPort.class),
            mock(DiaryMediaPort.class),
            mock(DiaryResults.class),
            save,
            clock);
    for (var features : unsupported)
      assertThatThrownBy(
              () ->
                  service.create(
                      new CreateDiaryEntryCommand(
                          1L, 2L, "일기", LocalDate.of(2026, 11, 17), features)))
          .isInstanceOf(DiaryFeatureUnavailableException.class);
    verifyNoInteractions(access, save);
  }

  @Test
  @DisplayName("#32-S23 웹을 거치지 않은 수정도 미지원 위치를 거부하고 원본에 접근하지 않는다")
  void editPolicy() {
    var service =
        new EditDiaryEntryService(
            access,
            mock(DiaryAttachmentsPort.class),
            mock(DiaryMediaPort.class),
            mock(DiaryResults.class),
            load,
            save,
            clock);
    for (var features : unsupported)
      assertThatThrownBy(
              () -> service.edit(new EditDiaryEntryCommand(1L, 2L, 3L, "일기", null, 0L, features)))
          .isInstanceOf(DiaryFeatureUnavailableException.class);
    verifyNoInteractions(access, load, save);
  }
}
