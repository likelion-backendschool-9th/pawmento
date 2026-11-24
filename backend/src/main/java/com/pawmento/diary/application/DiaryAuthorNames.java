package com.pawmento.diary.application;

import com.pawmento.diary.application.exception.StoredDiaryAuthorInvalidException;
import com.pawmento.diary.domain.DiaryAuthorName;
import com.pawmento.diary.domain.exception.InvalidDiaryAuthorNameException;

public final class DiaryAuthorNames {
  private DiaryAuthorNames() {}

  public static String stored(String name) {
    try {
      return new DiaryAuthorName(name).value();
    } catch (InvalidDiaryAuthorNameException failure) {
      throw new StoredDiaryAuthorInvalidException(failure);
    }
  }
}
