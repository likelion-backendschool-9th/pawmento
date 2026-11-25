package com.pawmento.diary.domain;

import com.pawmento.diary.domain.exception.InvalidDiaryAuthorNameException;

public record DiaryAuthorName(String value) {
  public DiaryAuthorName {
    if (value == null || value.isBlank() || value.strip().length() > 255)
      throw new InvalidDiaryAuthorNameException();
    value = value.strip();
  }
}
