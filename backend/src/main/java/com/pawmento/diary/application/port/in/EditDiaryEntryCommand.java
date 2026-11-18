package com.pawmento.diary.application.port.in;

import java.time.LocalDate;

public record EditDiaryEntryCommand(
    Long memberId,
    Long petId,
    Long recordId,
    String content,
    LocalDate recordedDate,
    Long version,
    DiaryWriteFeatures features) {
  public EditDiaryEntryCommand(
      Long memberId,
      Long petId,
      Long recordId,
      String content,
      LocalDate recordedDate,
      Long version) {
    this(memberId, petId, recordId, content, recordedDate, version, DiaryWriteFeatures.textOnly());
  }
}
