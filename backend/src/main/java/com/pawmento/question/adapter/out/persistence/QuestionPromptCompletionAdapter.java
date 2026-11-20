package com.pawmento.question.adapter.out.persistence;

import com.pawmento.question.application.port.out.QuestionPromptCompletionPort;
import com.pawmento.question.domain.QuestionPrompt;
import jakarta.persistence.EntityManager;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class QuestionPromptCompletionAdapter implements QuestionPromptCompletionPort {
  private final QuestionPromptJpaRepository prompts;
  private final EntityManager em;

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
}
