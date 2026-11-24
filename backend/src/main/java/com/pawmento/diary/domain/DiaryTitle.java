package com.pawmento.diary.domain;

import com.pawmento.diary.domain.exception.InvalidDiaryTitleException;

public record DiaryTitle(String value) {
  public DiaryTitle {
    if (value != null) {
      if (value
          .codePoints()
          .anyMatch(c -> Character.getType(c) == Character.CONTROL || c == 0x2028 || c == 0x2029))
        throw new InvalidDiaryTitleException();
      value = value.strip();
      if (value.isEmpty()) value = null;
      else if (value.codePointCount(0, value.length()) > 100)
        throw new InvalidDiaryTitleException();
    }
  }

  public static DiaryTitle empty() {
    return new DiaryTitle(null);
  }
}
