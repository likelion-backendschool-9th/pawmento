package com.pawmento.question.application.port.out;

import com.pawmento.question.domain.Question;
import java.util.List;

public interface SelectQuestionPort {
  Question select(List<Question> candidates);
}
