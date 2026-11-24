package com.pawmento.diary.application.port.in;

import com.pawmento.diary.domain.exception.InvalidDiaryTitleException;
import java.time.LocalDate;

public record EditDiaryEntryCommand(
    Long memberId,
    Long petId,
    Long diaryId,
    String content,
    LocalDate recordedDate,
    Long version,
    DiaryWriteFeatures features,
    DiaryTitleChange titleChange) {
  public EditDiaryEntryCommand {
    if (titleChange == null) throw new InvalidDiaryTitleException();
  }

  public EditDiaryEntryCommand(
      Long memberId,
      Long petId,
      Long diaryId,
      String content,
      LocalDate recordedDate,
      Long version,
      DiaryWriteFeatures features) {
    this(
        memberId,
        petId,
        diaryId,
        content,
        recordedDate,
        version,
        features,
        DiaryTitleChange.keep());
  }

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
