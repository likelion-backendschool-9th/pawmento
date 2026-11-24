package com.pawmento.diary.application.exception;

import com.pawmento.global.exception.ResourceNotFoundException;

public class DiaryAuthorNotFoundException extends ResourceNotFoundException {
  public DiaryAuthorNotFoundException(Throwable cause) {
    super("일기 작성자를 찾을 수 없어요.", cause);
  }
}
