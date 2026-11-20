package com.pawmento.question.adapter.out.persistence;

import com.pawmento.question.application.port.out.QuestionProgressStorePort;
import com.pawmento.question.domain.QuestionMode;
import com.pawmento.question.domain.QuestionProgress;
import com.pawmento.question.domain.QuestionPrompt;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class QuestionProgressPersistenceAdapter implements QuestionProgressStorePort {
  private final QuestionProgressJpaRepository progresses;
  private final QuestionPromptJpaRepository prompts;
  private final EntityManager em;

  public QuestionProgress lock(Long petId, QuestionMode mode) {
    return execute(
        () -> {
          em.createNativeQuery(
                  "select true from pg_advisory_xact_lock(hashtextextended(:key,0))", Boolean.class)
              .setParameter("key", "pawmento-question-progress:" + petId + ":" + mode.name())
              .getResultList();
          return progresses
              .findById(new QuestionProgressId(petId, mode))
              .map(
                  entity -> {
                    em.refresh(entity, LockModeType.PESSIMISTIC_WRITE);
                    return entity.toDomain();
                  })
              .orElseGet(() -> QuestionProgress.create(petId, mode));
        });
  }

  public Optional<QuestionPrompt> findCurrent(Long petId, QuestionMode mode) {
    return execute(() -> prompts.findCurrent(petId, mode).map(QuestionPromptJpaEntity::toDomain));
  }

  public QuestionPrompt loadPrompt(Long promptId) {
    return execute(
        () ->
            prompts
                .findById(promptId)
                .orElseThrow(() -> new IllegalStateException("현재 질문 행을 찾을 수 없습니다."))
                .toDomain());
  }

  public Long lastQuestionId(Long petId, QuestionMode mode) {
    return execute(
        () ->
            prompts
                .findFirstByPetIdAndModeOrderBySequenceDesc(petId, mode)
                .map(QuestionPromptJpaEntity::questionId)
                .orElse(null));
  }

  public Long savePrompt(QuestionPrompt prompt) {
    return execute(() -> prompts.saveAndFlush(QuestionPromptJpaEntity.from(prompt)).id());
  }

  public void saveProgress(QuestionProgress progress) {
    execute(
        () -> {
          var id = new QuestionProgressId(progress.getPetId(), progress.getMode());
          var entity =
              progresses.findById(id).orElseGet(() -> QuestionProgressJpaEntity.from(progress));
          entity.update(progress);
          progresses.saveAndFlush(entity);
          return null;
        });
  }

  private static <T> T execute(Supplier<T> action) {
    return QuestionPersistenceOperations.execute(action);
  }
}
