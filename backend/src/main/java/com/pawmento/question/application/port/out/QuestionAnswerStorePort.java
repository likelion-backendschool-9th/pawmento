package com.pawmento.question.application.port.out;

import com.pawmento.question.domain.*;
import java.time.LocalDate;
import java.util.*;

public interface QuestionAnswerStorePort {
  record Filter(
      Long petId,
      QuestionMode mode,
      Long promptId,
      Long authorMemberId,
      LocalDate from,
      LocalDate to,
      int page,
      int size) {}

  Optional<QuestionAnswer> find(Long petId, Long answerId);

  Optional<QuestionAnswer> lock(Long petId, Long answerId);

  boolean exists(Long promptId, Long authorMemberId);

  boolean existsByPetId(Long petId);

  QuestionAnswer save(QuestionAnswer answer);

  void delete(QuestionAnswer answer);

  List<QuestionAnswer> list(Filter filter);

  long count(Filter filter);
}
