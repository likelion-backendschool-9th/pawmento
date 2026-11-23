package com.pawmento.question.domain.exception;

import com.pawmento.global.exception.RuleViolationException;

public final class InvalidQuestionFilterException extends RuleViolationException {
  public InvalidQuestionFilterException() {
    super("질문 검색어는 널 문자 없이 300자까지, 페이지는 0 이상, 크기는 1~100으로 지정해 주세요.");
  }
}
