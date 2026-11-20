package com.pawmento.question.application.port.out;

import com.pawmento.question.domain.Question;
import com.pawmento.question.domain.QuestionMode;
import java.util.List;

/** 후속 질문 배정이 사용할 모드별 활성 풀. HTTP 관리 API가 아니다. */
public interface LoadActiveQuestionsPort {
  List<Question> loadByMode(QuestionMode mode);
}
