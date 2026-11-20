package com.pawmento.question.domain.exception;

import com.pawmento.global.exception.StoredDataInvalidException;

/** 저장된 현재 질문 포인터와 회차의 정합성이 깨진 경우다. */
public final class QuestionProgressInconsistentException extends StoredDataInvalidException {
  public QuestionProgressInconsistentException() {
    super("현재 질문과 진행 상태가 다릅니다.", null);
  }
}
