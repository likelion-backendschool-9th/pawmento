package com.pawmento.question.application.port.out;

import com.pawmento.question.domain.Question;
import java.util.Optional;

public interface LoadQuestionPort {
  Optional<Question> findById(Long questionId);
}
