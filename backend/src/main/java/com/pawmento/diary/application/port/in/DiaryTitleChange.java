package com.pawmento.diary.application.port.in;

import com.pawmento.diary.domain.DiaryTitle;
import com.pawmento.diary.domain.exception.InvalidDiaryTitleException;

public record DiaryTitleChange(boolean provided, DiaryTitle title) {
  public DiaryTitleChange {
    if (provided != (title != null)) throw new InvalidDiaryTitleException();
  }

  public static DiaryTitleChange keep() {
    return new DiaryTitleChange(false, null);
  }

  public static DiaryTitleChange replace(String value) {
    return new DiaryTitleChange(true, new DiaryTitle(value));
  }
}
