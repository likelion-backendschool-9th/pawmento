package com.pawmento.diary.domain.exception;

import com.pawmento.global.exception.RuleViolationException;

public class InvalidDiaryAuthorNameException extends RuleViolationException {
  public InvalidDiaryAuthorNameException() {
    super("일기 작성자 이름을 확인해 주세요.");
  }
}
