package com.pawmento.diary.application.port.in;

import java.time.LocalDate;

public record EditDiaryEntryCommand(
    Long memberId,
    Long petId,
    Long diaryId,
    String content,
    LocalDate recordedDate,
    Long version,
    DiaryWriteFeatures features) {
  public EditDiaryEntryCommand(
      Long memberId,
      Long petId,
      Long diaryId,
      String content,
      LocalDate recordedDate,
      Long version) {
    this(memberId, petId, diaryId, content, recordedDate, version, DiaryWriteFeatures.textOnly());
  }
}
