package com.pawmento.diary.application.exception;

import com.pawmento.global.exception.ResourceNotFoundException;

public class DiaryEntryNotFoundException extends ResourceNotFoundException {
  public DiaryEntryNotFoundException() {
    super("일기를 찾을 수 없어요.");
  }

  public DiaryEntryNotFoundException(Throwable cause) {
    super("일기를 찾을 수 없어요.", cause);
  }
}
