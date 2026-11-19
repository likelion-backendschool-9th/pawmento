package com.pawmento.diary.domain.exception;

import com.pawmento.global.exception.RuleViolationException;

public class InvalidDiaryQueryException extends RuleViolationException {
  public InvalidDiaryQueryException() {
    super("조회 날짜와 페이지를 확인해 주세요.");
  }
}
