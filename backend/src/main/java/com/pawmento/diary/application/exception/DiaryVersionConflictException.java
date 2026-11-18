package com.pawmento.diary.application.exception;

import com.pawmento.global.exception.StateConflictException;

public class DiaryVersionConflictException extends StateConflictException {
  public DiaryVersionConflictException() {
    super("일기가 변경됐어요. 다시 확인해 주세요.");
  }

  public DiaryVersionConflictException(Throwable cause) {
    super("일기가 변경됐어요. 다시 확인해 주세요.", cause);
  }
}
