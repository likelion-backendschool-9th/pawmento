package com.pawmento.diary.application.exception;

import com.pawmento.global.exception.StoredDataInvalidException;

public class StoredDiaryTitleInvalidException extends StoredDataInvalidException {
  public StoredDiaryTitleInvalidException(Throwable cause) {
    super("저장된 일기 제목이 올바르지 않습니다.", cause);
  }
}
