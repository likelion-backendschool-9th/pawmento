package com.pawmento.diary.adapter.in.web.exception;

import com.pawmento.global.exception.RuleViolationException;

public class InvalidDiaryRequestException extends RuleViolationException {
  public InvalidDiaryRequestException() {
    super("일기 요청 형식을 확인해 주세요.");
  }
}
