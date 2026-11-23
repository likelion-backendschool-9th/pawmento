package com.pawmento.question.application.port.out;

import com.pawmento.question.domain.Question;
import com.pawmento.question.domain.QuestionFilter;
import java.util.List;

public interface LoadQuestionsPort {
  List<Question> list(QuestionFilter filter);
  long count(QuestionFilter filter);
}
