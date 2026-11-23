package com.pawmento.question.application.port.out;

import com.pawmento.question.domain.Question;

public interface SaveQuestionPort {
  /** 신규 원본 저장. 기존 시드 원본 수정은 별도 변경 계약으로 구현한다. */
  Question save(Question question);
}
