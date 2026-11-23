package com.pawmento.question.domain;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** 질문 원본·배정·답변의 새 시각을 통일한다. 복원된 기존 시각은 바꾸지 않는다. */
final class QuestionTime {
  private QuestionTime() {}

  static Instant forWrite(Instant time) {
    return time == null ? null : time.truncatedTo(ChronoUnit.MICROS);
  }
}
