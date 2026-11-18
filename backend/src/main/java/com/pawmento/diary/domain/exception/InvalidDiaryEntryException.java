package com.pawmento.diary.domain.exception;

import com.pawmento.global.exception.RuleViolationException;

public class InvalidDiaryEntryException extends RuleViolationException {
  public InvalidDiaryEntryException() {
    super("일기 내용과 날짜를 확인해 주세요.");
  }
}
