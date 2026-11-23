package com.pawmento.question.adapter.out.persistence;

import com.pawmento.question.application.port.out.*;
import com.pawmento.question.domain.Question;
import com.pawmento.question.domain.QuestionFilter;
import com.pawmento.question.domain.QuestionMode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class QuestionPersistenceAdapter implements LoadActiveQuestionsPort, LoadQuestionsPort, LoadQuestionPort, SaveQuestionPort {
  private final QuestionJpaRepository repository;
  @PersistenceContext private EntityManager entityManager;

  @Override
  public List<Question> loadByMode(QuestionMode mode) {
    if (mode == null) {
      throw new IllegalArgumentException("질문 조회 모드가 필요합니다.");
    }
    return QuestionPersistenceOperations.execute(
        () ->
            repository.findByModeAndEnabledTrueOrderByIdAsc(mode).stream()
                .map(QuestionJpaEntity::toDomain)
                .toList());
  }

  public Question save(Question question) {
    return QuestionPersistenceOperations.execute(
        () -> {
          var saved = repository.saveAndFlush(QuestionJpaEntity.fromNew(question));
          // PostgreSQL이 저장한 시각 정밀도로 응답해 이후 목록·상세와 일치시킨다.
          entityManager.refresh(saved);
          return saved.toDomain();
        });
  }

  public Optional<Question> findById(Long id) {
    return QuestionPersistenceOperations.execute(() -> repository.findById(id).map(QuestionJpaEntity::toDomain));
  }

  public List<Question> list(QuestionFilter f) {
    return QuestionPersistenceOperations.execute(() -> repository.findAdmin(f.mode(), f.enabled(),
        pattern(f.search()), PageRequest.of(f.page(), f.size())).stream().map(QuestionJpaEntity::toDomain).toList());
  }

  public long count(QuestionFilter f) {
    return QuestionPersistenceOperations.execute(() -> repository.countAdmin(f.mode(), f.enabled(), pattern(f.search())));
  }

  private static String pattern(String search) {
    return "%" + search.toLowerCase(Locale.ROOT).replace("!", "!!").replace("%", "!%")
        .replace("_", "!_") + "%";
  }
}
