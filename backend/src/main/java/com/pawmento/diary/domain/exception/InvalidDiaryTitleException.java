package com.pawmento.diary.domain.exception;

import com.pawmento.global.exception.RuleViolationException;

public class InvalidDiaryTitleException extends RuleViolationException {
  public InvalidDiaryTitleException() {
    super("제목은 필수이며 줄바꿈 없는 100자 이하로 입력해 주세요.");
  }
}
