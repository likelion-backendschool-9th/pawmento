package com.pawmento.diary.domain;

import com.pawmento.diary.domain.exception.InvalidDiaryTitleException;

public record DiaryTitle(String value) {
  public DiaryTitle {
    if (value != null) {
      if (value
          .codePoints()
          .anyMatch(
              c ->
                  Character.getType(c) == Character.CONTROL
                      || Character.getType(c) == Character.SURROGATE
                      || c == 0x2028
                      || c == 0x2029)) throw new InvalidDiaryTitleException();
      value = value.strip();
      if (value.isEmpty()) value = null;
      else if (value.codePointCount(0, value.length()) > 100)
        throw new InvalidDiaryTitleException();
    }
  }

  public DiaryTitle requirePresent() {
    if (value == null) throw new InvalidDiaryTitleException();
    return this;
  }

  public static DiaryTitle restore(String raw) {
    var title = new DiaryTitle(raw);
    if (!java.util.Objects.equals(raw, title.value())) throw new InvalidDiaryTitleException();
    return title;
  }

  public static DiaryTitle empty() {
    return new DiaryTitle(null);
  }
}
