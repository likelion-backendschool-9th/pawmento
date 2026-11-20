package com.pawmento.question.application.exception;

import com.pawmento.global.exception.StorageUnavailableException;

public final class QuestionPersistenceUnavailableException extends StorageUnavailableException {
  public QuestionPersistenceUnavailableException(Throwable cause) {
    super("질문을 불러오지 못했어요. 잠시 후 다시 시도해 주세요.", cause);
  }
}
