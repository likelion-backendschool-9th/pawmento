package com.pawmento.question.application.port.out;

import com.pawmento.question.domain.Question;

public interface UpdateQuestionPort {
  /** 기존 원본만 갱신하며 읽은 version과 실제 저장 시 version의 충돌을 검사한다. */
  Question update(Question question);
}
