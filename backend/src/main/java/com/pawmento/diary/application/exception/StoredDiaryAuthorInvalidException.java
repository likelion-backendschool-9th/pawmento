package com.pawmento.diary.application.exception;

import com.pawmento.global.exception.StoredDataInvalidException;

public class StoredDiaryAuthorInvalidException extends StoredDataInvalidException {
  public StoredDiaryAuthorInvalidException(Throwable cause) {
    super("저장된 일기 작성자 정보가 올바르지 않습니다.", cause);
  }
}
