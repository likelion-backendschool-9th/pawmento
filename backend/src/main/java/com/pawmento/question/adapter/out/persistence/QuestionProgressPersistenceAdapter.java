package com.pawmento.question.adapter.out.persistence;

import com.pawmento.question.application.exception.StoredQuestionInvalidException;
import com.pawmento.question.application.port.out.LoadQuestionPromptHistoryPort;
import com.pawmento.question.application.port.out.QuestionProgressStorePort;
import com.pawmento.question.application.port.out.QuestionPromptCompletionPort;
import com.pawmento.question.domain.QuestionMode;
import com.pawmento.question.domain.QuestionProgress;
import com.pawmento.question.domain.QuestionPrompt;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class QuestionProgressPersistenceAdapter
    implements QuestionProgressStorePort,
        QuestionPromptCompletionPort,
        LoadQuestionPromptHistoryPort {
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
                .orElseThrow(
                    () ->
                        new StoredQuestionInvalidException(
                            new IllegalStateException("현재 질문 행을 찾을 수 없습니다.")))
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

  public Optional<QuestionPrompt> find(Long petId, Long promptId) {
    return QuestionPersistenceOperations.execute(
        () ->
            prompts
                .findById(promptId)
                .map(
                    entity -> {
                      em.refresh(entity);
                      return entity.toDomain();
                    })
                .filter(p -> p.getPetId().equals(petId)));
  }

  public void complete(QuestionPrompt prompt) {
    QuestionPersistenceOperations.execute(
        () -> {
          var entity = prompts.findById(prompt.getId()).orElseThrow();
          entity.complete(prompt);
          prompts.flush();
          return null;
        });
  }

  public Page list(Long petId, QuestionMode mode, Long memberId, int page, int size) {
    return execute(
        () -> {
          var name = mode == null ? null : mode.name();
          var ids = prompts.historyIds(petId, name, size, (long) page * size);
          long count = prompts.historyCount(petId, name);
          var items =
              ids.isEmpty()
                  ? List.<Summary>of()
                  : summaries(petId, memberId, prompts.findByIdInOrderByAssignedAtDescIdDesc(ids));
          return new Page(items, count);
        });
  }

  public Optional<Summary> findSummary(Long petId, Long promptId, Long memberId) {
    return execute(
        () ->
            prompts
                .findByIdAndPetId(promptId, petId)
                .map(p -> summaries(petId, memberId, List.of(p)).getFirst()));
  }

  private List<Summary> summaries(Long petId, Long memberId, List<QuestionPromptJpaEntity> rows) {
    var counts =
        prompts
            .summarizeAnswers(
                petId, rows.stream().map(QuestionPromptJpaEntity::id).toList(), memberId)
            .stream()
            .collect(
                Collectors.toMap(
                    QuestionPromptJpaRepository.AnswerSummary::getPromptId, Function.identity()));
    return rows.stream()
        .map(
            row -> {
              var count = counts.get(row.id());
              return new Summary(
                  row.toDomain(),
                  count == null ? 0 : count.getAnswerCount(),
                  count == null ? null : count.getMyAnswerId());
            })
        .toList();
  }

  private static <T> T execute(Supplier<T> action) {
    return QuestionPersistenceOperations.execute(action);
  }
}
