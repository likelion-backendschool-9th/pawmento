package com.pawmento.question.application.port.out;

import com.pawmento.question.application.port.in.*;
import java.util.List;

public interface QuestionMediaPort {
  void lock(Long member, Long pet, List<Long> ids);

  void validate(Long member, Long pet, List<Long> old, List<Long> next);

  void change(Long member, Long pet, List<Long> old, List<Long> next);

  List<QuestionAttachment> metadata(Long pet, List<Long> ids);

  GetQuestionFileResult file(Long pet, Long asset, boolean thumbnail);
}
