package com.pawmento.question.application.exception;

import com.pawmento.global.exception.StoredDataInvalidException;

public final class StoredQuestionInvalidException extends StoredDataInvalidException {
  public StoredQuestionInvalidException(Throwable cause) {
    super("저장된 질문이 도메인 규칙에 맞지 않습니다.", cause);
  }
}
