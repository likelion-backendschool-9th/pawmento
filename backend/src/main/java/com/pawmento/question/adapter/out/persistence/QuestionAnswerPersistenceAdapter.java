package com.pawmento.question.adapter.out.persistence;

import com.pawmento.question.application.port.out.QuestionAnswerStorePort;
import com.pawmento.question.application.port.out.QuestionAttachmentsPort;
import com.pawmento.question.domain.*;
import com.pawmento.question.domain.exception.QuestionAnswerConflictException;
import jakarta.persistence.*;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import org.postgresql.util.PSQLException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
class QuestionAnswerPersistenceAdapter implements QuestionAnswerStorePort, QuestionAttachmentsPort {
  private final QuestionAnswerJpaRepository answers;
  private final QuestionAttachmentJpaRepository attachmentRows;
  private final EntityManager em;

  public Optional<QuestionAnswer> find(Long pet, Long id) {
    return run(() -> answers.findByIdAndPetId(id, pet).map(QuestionAnswerJpaEntity::toDomain));
  }

  public Optional<QuestionAnswer> lock(Long pet, Long id) {
    return run(
        () ->
            answers
                .findByIdAndPetId(id, pet)
                .map(
                    entity -> {
                      em.refresh(entity, LockModeType.PESSIMISTIC_WRITE);
                      return entity.toDomain();
                    }));
  }

  public boolean exists(Long prompt, Long author) {
    return run(() -> answers.existsByPrompt_IdAndAuthorMemberId(prompt, author));
  }

  public boolean existsByPetId(Long pet) {
    return run(() -> answers.existsByPetId(pet));
  }

  public QuestionAnswer save(QuestionAnswer answer) {
    return run(
        () -> {
          QuestionAnswerJpaEntity entity;
          if (answer.getId() == null)
            entity =
                QuestionAnswerJpaEntity.from(
                    answer,
                    em.getReference(QuestionPromptJpaEntity.class, answer.getPrompt().getId()));
          else {
            entity = answers.findById(answer.getId()).orElseThrow();
            if (answer.isEdited()) {
              if (answers.updateVersion(
                      answer.getId(),
                      answer.getVersion(),
                      answer.getContent(),
                      answer.getUpdatedAt())
                  != 1) throw new QuestionAnswerConflictException();
              em.refresh(entity);
            }
          }
          return answers.saveAndFlush(entity).toDomain();
        });
  }

  public void delete(QuestionAnswer answer) {
    run(
        () -> {
          answers.deleteById(answer.getId());
          answers.flush();
          return null;
        });
  }

  public List<QuestionAnswer> list(Filter f) {
    return run(
        () -> {
          var ids =
              answers.findPageIds(
                  f.petId(),
                  f.mode() == null ? null : f.mode().name(),
                  f.promptId(),
                  f.authorMemberId(),
                  f.from(),
                  f.to(),
                  f.size(),
                  (long) f.page() * f.size());
          return ids.isEmpty()
              ? List.of()
              : answers.findByIdInOrderByAnsweredAtDescIdDesc(ids).stream()
                  .map(QuestionAnswerJpaEntity::toDomain)
                  .toList();
        });
  }

  public long count(Filter f) {
    return run(
        () ->
            answers.countEntries(
                f.petId(),
                f.mode() == null ? null : f.mode().name(),
                f.promptId(),
                f.authorMemberId(),
                f.from(),
                f.to()));
  }

  public Map<Long, List<Long>> findAll(List<Long> ids) {
    if (ids.isEmpty()) return Map.of();
    return run(
        () -> {
          Map<Long, List<Long>> result = new HashMap<>();
          for (var row : attachmentRows.findForParents(ids))
            result
                .computeIfAbsent(row.getId().getAnswerId(), k -> new ArrayList<>())
                .add(row.getId().getAssetId());
          return result;
        });
  }

  @Transactional
  public void replace(Long id, List<Long> assets) {
    run(
        () -> {
          // Remove managed links and flush before inserting reused composite IDs/order slots.
          // Bulk delete would leave previously loaded link entities stale in this transaction.
          attachmentRows.deleteById_AnswerId(id);
          attachmentRows.flush();
          attachmentRows.saveAllAndFlush(
              IntStream.range(0, assets.size())
                  .mapToObj(i -> QuestionAttachmentJpaEntity.from(id, assets.get(i), i))
                  .toList());
          return null;
        });
  }

  public boolean existsByAssetId(Long id) {
    return run(() -> attachmentRows.existsById_AssetId(id));
  }

  private static <T> T run(Supplier<T> action) {
    return QuestionPersistenceOperations.execute(
        () -> {
          try {
            return action.get();
          } catch (RuntimeException failure) {
            for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
              if (cause instanceof PSQLException sql
                  && "23505".equals(sql.getSQLState())
                  && sql.getServerErrorMessage() != null
                  && "uk_question_answer_author"
                      .equals(sql.getServerErrorMessage().getConstraint()))
                throw new QuestionAnswerConflictException();
              if (cause.getCause() == cause) break;
            }
            throw failure;
          }
        });
  }
}
