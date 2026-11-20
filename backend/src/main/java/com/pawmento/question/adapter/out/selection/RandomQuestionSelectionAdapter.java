package com.pawmento.question.adapter.out.selection;

import com.pawmento.question.application.port.out.SelectQuestionPort;
import com.pawmento.question.domain.Question;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Component;

@Component
class RandomQuestionSelectionAdapter implements SelectQuestionPort {
  public Question select(List<Question> candidates) {
    return candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
  }
}
