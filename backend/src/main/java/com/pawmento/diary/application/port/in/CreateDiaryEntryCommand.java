package com.pawmento.diary.application.port.in;

import java.time.LocalDate;

public record CreateDiaryEntryCommand(
    Long memberId,
    Long petId,
    String content,
    LocalDate recordedDate,
    DiaryWriteFeatures features) {
  public CreateDiaryEntryCommand(
      Long memberId, Long petId, String content, LocalDate recordedDate) {
    this(memberId, petId, content, recordedDate, DiaryWriteFeatures.textOnly());
  }
}
