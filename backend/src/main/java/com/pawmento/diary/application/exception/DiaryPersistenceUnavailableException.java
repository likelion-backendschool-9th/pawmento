package com.pawmento.diary.application.exception;

import com.pawmento.global.exception.StorageUnavailableException;

public class DiaryPersistenceUnavailableException extends StorageUnavailableException {
  public DiaryPersistenceUnavailableException(Throwable cause) {
    super("다이어리 저장소에 일시적으로 접근할 수 없습니다.", cause);
  }
}
