package com.pawmento.diary.application.port.in;

import com.pawmento.diary.domain.DiaryTitle;
import com.pawmento.diary.domain.exception.InvalidDiaryTitleException;
import java.time.LocalDate;

public record CreateDiaryEntryCommand(
    Long memberId,
    Long petId,
    String content,
    LocalDate recordedDate,
    DiaryWriteFeatures features,
    DiaryTitle title) {
  public CreateDiaryEntryCommand {
    if (title == null) throw new InvalidDiaryTitleException();
  }

  public CreateDiaryEntryCommand(
      Long memberId,
      Long petId,
      String content,
      LocalDate recordedDate,
      DiaryWriteFeatures features) {
    this(memberId, petId, content, recordedDate, features, DiaryTitle.empty());
  }

  public static CreateDiaryEntryCommand withTitle(
      Long memberId,
      Long petId,
      String content,
      LocalDate date,
      DiaryWriteFeatures features,
      String title) {
    return new CreateDiaryEntryCommand(
        memberId, petId, content, date, features, new DiaryTitle(title));
  }

  public CreateDiaryEntryCommand(
      Long memberId, Long petId, String content, LocalDate recordedDate) {
    this(memberId, petId, content, recordedDate, DiaryWriteFeatures.textOnly());
  }
}
